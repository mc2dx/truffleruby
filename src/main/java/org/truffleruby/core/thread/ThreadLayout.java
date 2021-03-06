/*
 * Copyright (c) 2015, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.thread;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;

import org.truffleruby.core.InterruptMode;
import org.truffleruby.core.basicobject.BasicObjectLayout;
import org.truffleruby.core.fiber.FiberManager;
import org.truffleruby.core.tracepoint.TracePointState;
import org.truffleruby.language.threadlocal.ThreadLocalGlobals;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectFactory;
import com.oracle.truffle.api.object.ObjectType;
import com.oracle.truffle.api.object.dsl.Layout;
import com.oracle.truffle.api.object.dsl.Nullable;
import com.oracle.truffle.api.object.dsl.Volatile;

@Layout
public interface ThreadLayout extends BasicObjectLayout {

    String THREAD_LOCAL_VARIABLES_IDENTIFIER = "@thread_local_variables";
    String RECURSIVE_OBJECTS_IDENTIFIER = "@recursive_objects";
    String RANDOMIZER_IDENTIFIER = "@randomizer";
    String REPORT_ON_EXCEPTION_IDENTIFIER = "@report_on_exception";
    String ABORT_ON_EXCEPTION_IDENTIFIER = "@abort_on_exception";

    DynamicObjectFactory createThreadShape(
            DynamicObject logicalClass,
            DynamicObject metaClass);

    Object[] build(
            ThreadLocalGlobals threadLocalGlobals,
            @Volatile InterruptMode interruptMode, // needs to be volatile for fibers implemented by threads
            @Volatile ThreadStatus status,
            List<Lock> ownedLocks,
            @Nullable FiberManager fiberManager,
            CountDownLatch finishedLatch,
            DynamicObject threadLocalVariables,
            DynamicObject recursiveObjects,
            DynamicObject randomizer,
            TracePointState tracePointState,
            boolean reportOnException,
            boolean abortOnException,
            @Nullable @Volatile Thread thread,
            @Nullable @Volatile DynamicObject exception,
            @Nullable @Volatile Object value,
            AtomicBoolean wakeUp,
            @Volatile int priority,
            ThreadLocalBuffer ioBuffer,
            Object threadGroup,
            String sourceLocation,
            Object name);

    boolean isThread(ObjectType objectType);

    boolean isThread(DynamicObject object);

    FiberManager getFiberManager(DynamicObject object);

    void setFiberManagerUnsafe(DynamicObject object, FiberManager value);

    CountDownLatch getFinishedLatch(DynamicObject object);

    void setFinishedLatch(DynamicObject object, CountDownLatch value);

    ThreadLocalGlobals getThreadLocalGlobals(DynamicObject object);

    List<Lock> getOwnedLocks(DynamicObject object);

    DynamicObject getRandomizer(DynamicObject object);

    TracePointState getTracePointState(DynamicObject object);

    boolean getReportOnException(DynamicObject object);

    boolean getAbortOnException(DynamicObject object);

    void setAbortOnException(DynamicObject object, boolean value);

    InterruptMode getInterruptMode(DynamicObject object);

    void setInterruptMode(DynamicObject object, InterruptMode value);

    Thread getThread(DynamicObject object);

    void setThread(DynamicObject object, Thread value);

    ThreadStatus getStatus(DynamicObject object);

    void setStatus(DynamicObject object, ThreadStatus value);

    DynamicObject getException(DynamicObject object);

    void setException(DynamicObject object, DynamicObject value);

    Object getValue(DynamicObject object);

    void setValue(DynamicObject object, Object value);

    AtomicBoolean getWakeUp(DynamicObject object);

    int getPriority(DynamicObject object);

    void setPriority(DynamicObject object, int value);

    ThreadLocalBuffer getIoBuffer(DynamicObject object);

    void setIoBuffer(DynamicObject object, ThreadLocalBuffer value);

    Object getThreadGroup(DynamicObject object);

    void setThreadGroup(DynamicObject object, Object value);

    Object getName(DynamicObject object);

    void setName(DynamicObject object, Object value);

    String getSourceLocation(DynamicObject object);

    void setSourceLocation(DynamicObject object, String value);

}
