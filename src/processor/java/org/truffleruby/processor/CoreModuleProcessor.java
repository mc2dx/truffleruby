/*
 * Copyright (c) 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.processor;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

import org.truffleruby.builtins.CoreMethod;
import org.truffleruby.builtins.CoreModule;
import org.truffleruby.builtins.Primitive;

import com.oracle.truffle.api.dsl.Specialization;

@SupportedAnnotationTypes("org.truffleruby.builtins.CoreModule")
public class CoreModuleProcessor extends AbstractProcessor {

    private static final String SUFFIX = "Builtins";
    private static final Set<String> KEYWORDS;
    static {
        KEYWORDS = new HashSet<>();
        KEYWORDS.addAll(Arrays.asList(
                "alias",
                "and",
                "begin",
                "break",
                "case",
                "class",
                "def",
                "defined?",
                "do",
                "else",
                "elsif",
                "end",
                "ensure",
                "false",
                "for",
                "if",
                "in",
                "module",
                "next",
                "nil",
                "not",
                "or",
                "redo",
                "rescue",
                "retry",
                "return",
                "self",
                "super",
                "then",
                "true",
                "undef",
                "unless",
                "until",
                "when",
                "while",
                "yield"));
    }

    private final Set<String> processed = new HashSet<>();
    private TypeMirror virtualFrame;

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) {
        virtualFrame = processingEnv
                .getElementUtils()
                .getTypeElement("com.oracle.truffle.api.frame.VirtualFrame")
                .asType();

        if (!annotations.isEmpty()) {
            for (Element element : roundEnvironment.getElementsAnnotatedWith(CoreModule.class)) {
                try {
                    processCoreModule((TypeElement) element);
                } catch (IOException e) {
                    processingEnv.getMessager().printMessage(Kind.ERROR, e.getClass() + " " + e.getMessage(), element);
                }
            }
        }

        return true;
    }

    private void processCoreModule(TypeElement coreModuleElement) throws IOException {
        final CoreModule coreModule = coreModuleElement.getAnnotation(CoreModule.class);

        final PackageElement packageElement = (PackageElement) coreModuleElement.getEnclosingElement();
        final String packageName = packageElement.getQualifiedName().toString();

        final String qualifiedName = coreModuleElement.getQualifiedName().toString();
        if (!processed.add(qualifiedName)) {
            // Already processed, do nothing. This seems an Eclipse bug.
            return;
        }

        final JavaFileObject output = processingEnv
                .getFiler()
                .createSourceFile(qualifiedName + SUFFIX, coreModuleElement);
        final FileObject rubyFile = processingEnv.getFiler().createResource(
                StandardLocation.SOURCE_OUTPUT,
                "core_module_stubs",
                coreModule.value().replace("::", "/") + ".rb",
                (Element[]) null);


        try (PrintStream stream = new PrintStream(output.openOutputStream(), true, "UTF-8")) {
            try (PrintStream rubyStream = new PrintStream(rubyFile.openOutputStream(), true, "UTF-8")) {

                final List<? extends Element> enclosedElements = coreModuleElement.getEnclosedElements();
                final boolean anyCoreMethod = anyCoreMethod(enclosedElements);

                stream.println("package " + packageName + ";");
                stream.println();
                stream.println("import org.truffleruby.builtins.CoreMethodNodeManager;");
                stream.println("import org.truffleruby.builtins.PrimitiveManager;");
                if (anyCoreMethod) {
                    stream.println("import org.truffleruby.language.Visibility;");
                }
                stream.println();
                stream.println("public class " + coreModuleElement.getSimpleName() + SUFFIX + " {");
                stream.println();
                stream.println(
                        "    public static void setup(CoreMethodNodeManager coreMethodManager, PrimitiveManager primitiveManager) {");

                rubyStream.println("raise 'this file is a stub file for development and should never be loaded'");
                rubyStream.println();
                rubyStream.println((coreModule.isClass() ? "class" : "module") + " " + coreModule.value());
                rubyStream.println();

                final StringBuilder rubyPrimitives = new StringBuilder();

                for (Element e : enclosedElements) {
                    if (e instanceof TypeElement) {
                        final TypeElement klass = (TypeElement) e;

                        final CoreMethod coreMethod = klass.getAnnotation(CoreMethod.class);
                        if (coreMethod != null) {
                            processCoreMethod(stream, rubyStream, coreModuleElement, coreModule, klass, coreMethod);
                        }

                        final Primitive primitive = e.getAnnotation(Primitive.class);
                        if (primitive != null) {
                            processPrimitive(stream, rubyPrimitives, coreModuleElement, klass, primitive);
                        }
                    }
                }

                stream.println("    }");
                stream.println();
                stream.println("}");

                rubyStream.println("end");
                rubyStream.println();

                rubyStream.println("module TrufflePrimitive");
                rubyStream.print(rubyPrimitives);
                rubyStream.println("end");
                rubyStream.println();
            }
        }

    }

    private void processPrimitive(
            PrintStream stream,
            StringBuilder rubyPrimitives,
            TypeElement element,
            TypeElement klass,
            Primitive primitive) {
        List<String> argumentNames = getArgumentNames(klass, false);

        final String nodeFactory = nodeFactoryName(element, klass);
        stream.println("        primitiveManager.addLazyPrimitive(" +
                quote(primitive.name()) + ", " + quote(nodeFactory) + ");");

        final StringJoiner arguments = new StringJoiner(", ");
        for (String argument : argumentNames) {
            arguments.add(argument);
        }

        rubyPrimitives
                .append("  def self.")
                .append(primitive.name())
                .append("(")
                .append(arguments)
                .append(")")
                .append('\n');
        rubyPrimitives.append("    # language=java").append('\n');
        rubyPrimitives
                .append("    /** @see ")
                .append(klass.getQualifiedName().toString())
                .append(" */")
                .append('\n');
        rubyPrimitives.append("  end").append('\n');
        rubyPrimitives.append('\n');
    }

    private void processCoreMethod(
            PrintStream stream,
            PrintStream rubyStream,
            TypeElement element,
            CoreModule coreModule,
            TypeElement klass,
            CoreMethod coreMethod) {
        final StringJoiner names = new StringJoiner(", ");
        for (String name : coreMethod.names()) {
            names.add(quote(name));
        }
        // final String className = klass.getQualifiedName().toString();
        final String nodeFactory = nodeFactoryName(element, klass);
        final boolean onSingleton = coreMethod.onSingleton() || coreMethod.constructor();
        stream.println("        coreMethodManager.addLazyCoreMethod(" + quote(nodeFactory) + ",");
        stream.println("                " +
                quote(coreModule.value()) + ", " +
                coreModule.isClass() + ", " +
                "Visibility." + coreMethod.visibility().name() + ", " +
                coreMethod.isModuleFunction() + ", " +
                onSingleton + ", " +
                coreMethod.neverSplit() + ", " +
                coreMethod.required() + ", " +
                coreMethod.optional() + ", " +
                coreMethod.rest() + ", " +
                (coreMethod.keywordAsOptional().isEmpty()
                        ? "null"
                        : quote(coreMethod.keywordAsOptional())) +
                ", " +
                names + ");");

        final boolean hasSelfArgument = !coreMethod.onSingleton() && !coreMethod.constructor() &&
                !coreMethod.isModuleFunction() &&
                coreMethod.needsSelf();

        final List<String> argumentNames;
        if (coreMethod.argumentNames().length == 0) {
            argumentNames = getArgumentNames(klass, hasSelfArgument);
        } else {
            if (coreMethod.argumentNames().length != getNumberOfArguments(coreMethod)) {
                processingEnv.getMessager().printMessage(
                        Kind.ERROR,
                        "The size of argumentNames does not match declared number of arguments.",
                        klass);
                argumentNames = new ArrayList<>();
            } else {
                argumentNames = Arrays.asList(coreMethod.argumentNames());
            }
        }

        if (argumentNames.isEmpty() && getNumberOfArguments(coreMethod) > 0) {
            processingEnv.getMessager().printMessage(
                    Kind.WARNING,
                    "Did not find argument names. If the class has inherited Specializations use org.truffleruby.builtins.CoreMethod.argumentNames",
                    klass);

            for (int i = 0; i < coreMethod.required(); i++) {
                argumentNames.add("req" + (i + 1));
            }
            for (int i = 0; i < coreMethod.optional(); i++) {
                argumentNames.add("opt" + (i + 1));
            }
            if (coreMethod.rest()) {
                argumentNames.add("args");
            }
            if (coreMethod.needsBlock()) {
                argumentNames.add("block");
            }
        }

        int index = 0;

        final StringJoiner args = new StringJoiner(", ");
        for (int i = 0; i < coreMethod.required(); i++) {
            args.add(argumentNames.get(index));
            index++;
        }
        for (int i = 0; i < coreMethod.optional(); i++) {
            args.add(argumentNames.get(index) + " = nil");
            index++;
        }
        if (coreMethod.rest()) {
            args.add("*" + argumentNames.get(index));
            index++;
        }
        if (!coreMethod.keywordAsOptional().isEmpty()) {
            // TODO (pitr-ch 03-Oct-2019): check interaction with names, or remove it
            args.add(coreMethod.keywordAsOptional() + ": :unknown_default_value");
        }
        if (coreMethod.needsBlock()) {
            args.add("&" + argumentNames.get(index));
        }

        rubyStream.println("  def " + (onSingleton ? "self." : "") + coreMethod.names()[0] + "(" + args + ")");
        rubyStream.println("    # language=java");
        rubyStream.println("    /** @see " + klass.getQualifiedName().toString() + " */");
        rubyStream.println("  end");

        for (int i = 1; i < coreMethod.names().length; i++) {
            rubyStream.println("  alias_method :" + coreMethod.names()[i] + ", :" + coreMethod.names()[0]);
        }
        if (coreMethod.isModuleFunction()) {
            rubyStream.println("  module_function :" + coreMethod.names()[0]);
        }
        rubyStream.println();
    }

    private int getNumberOfArguments(CoreMethod coreMethod) {
        return coreMethod.required() + coreMethod.optional() + (coreMethod.rest() ? 1 : 0) +
                (coreMethod.needsBlock() ? 1 : 0);
    }

    private List<String> getArgumentNames(TypeElement klass, boolean hasSelfArgument) {
        List<String> argumentNames = new ArrayList<>();
        List<VariableElement> argumentElements = new ArrayList<>();

        for (Element el : klass.getEnclosedElements()) {
            if (!(el instanceof ExecutableElement)) {
                continue; // we are interested only in executable elements
            }

            final ExecutableElement executableElement = (ExecutableElement) el;

            if (executableElement.getAnnotation(Specialization.class) == null) {
                continue; // we are interested only in Specialization methods
            }

            boolean addingArguments = argumentNames.isEmpty();

            int index = 0;
            boolean skippedSelf = false;
            for (VariableElement parameter : executableElement.getParameters()) {
                if (!parameter.getAnnotationMirrors().isEmpty()) {
                    continue; // we ignore arguments having annotations like @Cached
                }

                if (processingEnv.getTypeUtils().isSameType(parameter.asType(), virtualFrame)) {
                    continue;
                }

                if (hasSelfArgument && !skippedSelf) {
                    skippedSelf = true;
                    continue;
                }

                String name = parameter.getSimpleName().toString();
                if (addingArguments) {
                    argumentNames.add(name);
                    argumentElements.add(parameter);

                    if (KEYWORDS.contains(name)) {
                        processingEnv.getMessager().printMessage(
                                Kind.ERROR,
                                "The argument should not be a Ruby keyword.",
                                parameter);

                    }
                } else {
                    if (!argumentNames.get(index).equals(name)) {
                        processingEnv.getMessager().printMessage(
                                Kind.WARNING,
                                "The argument does not match with the first occurrence of this argument which was '" +
                                        argumentNames.get(index) + "'.",
                                parameter);
                    }
                }
                index++;
            }
        }

        return argumentNames;
    }

    private boolean anyCoreMethod(List<? extends Element> enclosedElements) {
        for (Element e : enclosedElements) {
            if (e instanceof TypeElement && e.getAnnotation(CoreMethod.class) != null) {
                return true;
            }
        }
        return false;
    }

    private String nodeFactoryName(TypeElement element, TypeElement klass) {
        return element.getQualifiedName() + "Factory$" + klass.getSimpleName() + "Factory";
    }

    private static String quote(String str) {
        return '"' + str + '"';
    }

}
