/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.thread;

import org.truffleruby.Layouts;
import org.truffleruby.language.RubyContextNode;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.DynamicObject;

public abstract class GetCurrentRubyThreadNode extends RubyContextNode {

    public static GetCurrentRubyThreadNode create() {
        return GetCurrentRubyThreadNodeGen.create();
    }

    public final DynamicObject execute() {
        return executeInternal(Boolean.TRUE);
    }

    /* We need to include a seemingly useless dynamic parameter, otherwise the Truffle DSL will assume calls with no
     * arguments such as getCurrentJavaThread() never change, but for instance the thread might change. */
    protected abstract DynamicObject executeInternal(Object dynamicParameter);

    /* Note: we need to check that the Fiber is still running on a Java thread to cache based on the Java thread. If the
     * Fiber finished its execution, the Java thread can be reused for another Fiber belonging to another Ruby Thread,
     * due to using a thread pool for Fibers. */
    @Specialization(
            guards = {
                    "getCurrentJavaThread(dynamicParameter) == cachedJavaThread",
                    "hasThread(dynamicParameter, cachedFiber)",
                    /* Cannot cache a Thread instance when pre-initializing */
                    "!preInitializing" },
            limit = "getCacheLimit()")
    protected DynamicObject getRubyThreadCached(Object dynamicParameter,
            @Cached("isPreInitializing()") boolean preInitializing,
            @Cached("getCurrentJavaThread(dynamicParameter)") Thread cachedJavaThread,
            @Cached("getCurrentRubyThread(dynamicParameter)") DynamicObject cachedRubyThread,
            @Cached("getCurrentFiber(cachedRubyThread)") DynamicObject cachedFiber) {
        return cachedRubyThread;
    }

    @Specialization(replaces = "getRubyThreadCached")
    protected DynamicObject getRubyThreadUncached(Object dynamicParameter) {
        return getCurrentRubyThread(dynamicParameter);
    }

    protected Thread getCurrentJavaThread(Object dynamicParameter) {
        return Thread.currentThread();
    }

    protected DynamicObject getCurrentRubyThread(Object dynamicParameter) {
        return getContext().getThreadManager().getCurrentThread();
    }

    protected DynamicObject getCurrentFiber(DynamicObject currentRubyThread) {
        return Layouts.THREAD.getFiberManager(currentRubyThread).getCurrentFiber();
    }

    protected boolean hasThread(Object dynamicParameter, DynamicObject fiber) {
        return Layouts.FIBER.getThread(fiber) != null;
    }

    protected boolean isPreInitializing() {
        return getContext().isPreInitializing();
    }

    protected int getCacheLimit() {
        return getContext().getOptions().THREAD_CACHE;
    }

}
