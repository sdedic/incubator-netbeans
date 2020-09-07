/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.openide.util;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openide.util.FutureOverrides.CompletionStageDelegate;
import org.openide.util.FutureOverrides.CompletionStageFuture;


/** A task that may be executed in a separate thread and permits examination of its status.
* Other threads can check if it is finished or wait for it
* to finish.
* <P>
* For example:
* <p><code><PRE>
* Runnable r = new Runnable () {
*   public void run () {
*     // do something
*   }
* };
* Task task = new Task (r);
* RequestProcessor.postRequest (task);
* </PRE></code>
* <p>In a different thread one can then test <CODE>task.isFinished ()</CODE>
* or wait for it with <CODE>task.waitFinished ()</CODE>.
*
* @author Jaroslav Tulach
*/
public class Task extends Object implements Runnable {
    /** Dummy task which is already finished. */
    public static final Task EMPTY = new Task();
    private static final Logger LOG = Logger.getLogger(Task.class.getName());

    static {
        EMPTY.finished = true;
    }

    /** map of subclasses to booleans whether they override waitFinished() or not
     */
    private static java.util.WeakHashMap<Class, Boolean> overrides;

    /** request processor for workarounding compatibility problem with
     * classes that do not override waitFinished (long)
     */
    private static RequestProcessor RP;

    /** what to run */
    final Runnable run;
    
    final Callable<?> call;
    
    final Class<?> resultType;
    
    private Object resultValue;
    
    /** flag if we have finished */
    private boolean finished;

    /** listeners for the finish of task (TaskListener) */
    private HashSet<TaskListener> list;
    
    private Privileged<?> futureBridge;
    
    private Throwable completedWithException;

    /** Create a new task.
    * The runnable should provide its own error-handling, as
    * by default thrown exceptions are simply logged and not rethrown.
    * @param run runnable to run that computes the task
    */
    public Task(Runnable run) {
        this.run = run;

        if (run == null) {
            finished = true;
        }
        call = null;
        resultType = null;
    }

    /** Constructor for subclasses that wants to control whole execution
    * itself.
    * @since 1.5
    */
    protected Task() {
        this.run = null;
        this.call = null;
        resultType = null;
    }
    
    /**
     * Creates a Task that produces value of a certain type.
     * @param call executable to produce the value
     * @param resType result type.
     */
    public <T> Task(Callable<T> call, Class<T> resType) {
        this.run = null;
        this.call = call;
        this.resultType = resType;
    }

    /** Test whether the task has finished running.
    * @return <code>true</code> if so
    */
    public final boolean isFinished() {
        synchronized (this) {
            return finished;
        }
    }

    /** Wait until the task is finished.
    * Changed not to be <code>final</code> in version 1.5
    */
    public void waitFinished() {
        synchronized (this) {
            while (!finished) {
                try {
                    wait();
                } catch (InterruptedException ex) {
                }
            }
        }
    }

    /** Wait until the task is finished, but only a given time.
    *  waitFinished(0) means indefinite timeout (similar to wait(0))
    *  @param milliseconds time in milliseconds to wait for the result
    *  @exception InterruptedException when the waiting has been interrupted
    *  @return true if the task is really finished, or false if the time out
    *     has been exceeded
    *  @since 5.0
    */
    public boolean waitFinished(long milliseconds) throws InterruptedException {
        synchronized (this) {
            if (overridesTimeoutedWaitFinished()) {
                // the the task overrides waitFinished (timeout) or is 
                // one of the basic tasks, then we can just simply do our bese
                // code. Otherwise we have to execute threading workaround
                if (finished) {
                    return true;
                }

                long expectedEnd = System.currentTimeMillis() + milliseconds;

                for (;;) {
                    LOG.log(Level.FINE, "About to wait {0} ms", milliseconds);
                    wait(milliseconds);

                    if (finished) {
                        LOG.log(Level.FINER, "finished, return"); // NOI18N
                        return true;
                    }
                    
                    if (milliseconds == 0) {
                        LOG.log(Level.FINER, "infinite wait, again"); // NOI18N
                        continue;
                    }
                    
                    long now = System.currentTimeMillis();
                    long remains = expectedEnd - now;
                    LOG.log(Level.FINER, "remains {0} ms", remains);
                    if (remains <= 0) {
                        LOG.log(Level.FINER, "exit, timetout");
                        return false;
                    }

                    milliseconds = remains;
                }
            }
        }

        // as we know that RequestProcessor implements the waitFinished(long)
        // correctly we just post a task for waitFinished() into some
        // of its threads and wait just the given milliseconds time
        // for the result, by that we can guarantee the semantics
        // of the call
        class Run implements Runnable {
            @Override
            public void run() {
                Task.this.waitFinished();
            }
        }

        LOG.fine("Using compatibility waiting");
        RequestProcessor.Task task = RP.post(new Run());

        return task.waitFinished(milliseconds);
    }

    /** Changes the state of the task to be running. Any call after this
    * one and before notifyFinished to waitFinished blocks.
    * @since 1.5
    */
    protected final void notifyRunning() {
        synchronized (this) {
            RequestProcessor.logger().log(Level.FINE, "notifyRunning: {0}", this); // NOI18N
            this.finished = false;
            completedWithException = null;
            // reset the bridge, runs again for some reason.
            if (futureBridge != null && futureBridge.future().isDone()) {
                futureBridge = null;
            }
            notifyAll();
        }
    }
    
    /**
     * Returns preferred executor. The executor will be used in the by {@link Future}s and
     * {@link CompletionStages} created by the Task for executing code. If {@code null} is
     * returned, system default executor will be used.
     * 
     * @return preferred executor. 
     */
    protected Executor  preferredExecutor() {
        return null;
    }
    
    /**
     * Records exceptional completion of the task. Called from the default {@link #run} 
     * implementation, if the Runnable failed with an exception. Even if {@link #notifyFinished} 
     * is called later, the Task will still report an exception. 
     * <p>
     * If a {@link Completable} is created for this Task, its {@link Future#get} will throw
     * an {@link ExecutionException}.
     * 
     * @param exception exception.
     * @since 9.17
     */
    protected final void notifyFinishedExceptionally(Throwable exception) {
        Privileged fut;
        synchronized (this) {
            completedWithException = exception;
            fut = futureBridge;
        }
        if (fut != null) {
            fut.completeExceptionally(exception);
        }
    }
    
    /**
     * Returns the exception thrown during this task's execution (if any).
     * @return exception, or {@code null} if none.
     * @since 9.17
     */
    public final Throwable getException() {
        return completedWithException;
    }
    
    /** Notify all waiters that this task has finished.
    * @see #run
    */
    protected final void notifyFinished() {
        notifyFinished(null);
    }        
    
    protected final <T> void notifyFinished(T result) {
        if (result != null && resultType != null) {
            if (!resultType.isInstance(result)) {
                throw new IllegalArgumentException("Invalid type: " + result.getClass() + ", Expected: " + resultType);
            }
        }
        if (resultType == null & result != null) {
            throw new IllegalStateException("Unexpected result value: " + result.getClass());
        }
        Iterator<TaskListener> it;
        Privileged priv;
        
        synchronized (this) {
            finished = true;
            resultValue = result;
            RequestProcessor.logger().log(Level.FINE, "notifyFinished: {0}", this); // NOI18N
            notifyAll();

            priv = this.futureBridge;
            // fire the listeners
            if (list == null) {
                if (priv == null) {
                    return;
                } else {
                    it = Collections.<TaskListener>emptyList().iterator();
                }
            } else {
                it = ((HashSet) list.clone()).iterator();
            }
        }
        
        while (it.hasNext()) {
            TaskListener l = it.next();
            l.taskFinished(this);
        }
        if (priv != null && !priv.future().isDone()) {
            priv.complete(result);
        }
    }

    /** Start the task.
    * When it finishes (even with an exception) it calls
    * {@link #notifyFinished}.
    * Subclasses may override this method, but they
    * then need to call {@link #notifyFinished} explicitly.
    * <p>Note that this call runs synchronously, but typically the creator
    * of the task will call this method in a separate thread.
    */
    public void run() {
        Object result = null;
        try {
            notifyRunning();

            if (call != null) {
                try {
                    result = call.call();
                } catch (Exception ex) {
                    notifyFinishedExceptionally(ex);
                    return;
                }
            }
            if (run != null) {
                run.run();
            }
        } catch (RuntimeException | Error x) {
            notifyFinishedExceptionally(x);
        } finally {
            notifyFinished(result);
        }
    }

    /** Add a listener to the task. The listener will be called once the 
     * task {@link #isFinished()}. In case the task is already finished, the
     * listener is called immediately.
     * 
     * @param l the listener to add
     */
    public void addTaskListener(TaskListener l) {
        boolean callNow;
        synchronized (this) {
            if (list == null) {
                list = new HashSet<TaskListener>();
            }
            list.add(l);
            
            callNow = finished;
        }

        if (callNow) {
            l.taskFinished(this);
        }
    }

    /** Remove a listener from the task.
    * @param l the listener to remove
    */
    public synchronized void removeTaskListener(TaskListener l) {
        if (list == null) {
            return;
        }

        list.remove(l);
    }

    public String toString() {
        return "task " + run; // NOI18N
    }

    /** Checks whether the class overrides wait finished.
     */
    private boolean overridesTimeoutedWaitFinished() {
        // yes we implement it corretly
        if (getClass() == Task.class) {
            return true;
        }

        // RequestProcessor.Task overrides correctly
        if (getClass() == RequestProcessor.Task.class) {
            return true;
        }

        java.util.WeakHashMap<Class,Boolean> m;
        Boolean does;

        synchronized (Task.class) {
            if (overrides == null) {
                overrides = new java.util.WeakHashMap<Class, Boolean>();
                RP = new RequestProcessor("Timeout waitFinished compatibility processor", 255); // NOI18N
            }

            m = overrides;

            does = m.get(getClass());

            if (does != null) {
                return does.booleanValue();
            }

            try {
                java.lang.reflect.Method method = getClass().getMethod("waitFinished", new Class[] { Long.TYPE }); // NOI18N
                does = Boolean.valueOf(method.getDeclaringClass() != Task.class);
                m.put(getClass(), does);

                return does.booleanValue();
            } catch (Exception ex) {
                Exceptions.printStackTrace(ex);

                return true;
            }
        }
    }

    /** Reveal the identity of the worker runnable.
     * Used for debugging from RequestProcessor.
     */
    String debug() {
        return (run == null) ? "null" : run.getClass().getName();
    }
    
    /**
     * This interface provides access to the CompletableFuture's termination methods.
     * If the implementation blocks these calls for clients, the creator should be still
     * able to terminate the Future as needed.
     * <p>
     * The methods are extracted from {@link CompletableFuture} interface.
     * 
     * @param <T> type of Future's data
     */
    public static interface Privileged<T> {
        public Completable<T> future();
        
        /**
         * Marks the future as cancelled.
         * @param stopIfRunning 
         * @return true, if successful
         */
        public boolean cancel(boolean stopIfRunning);
        
        /**
         * Completes the Future normally, with the given value.
         * @param val
         * @return true, if successful
         */
        public boolean complete(T val);
        
        /**
         * Completes the Future exceptionally.
         * @param e the thrown exception.
         * @return true, if successful.
         */
        public boolean completeExceptionally(Throwable e);
    }
    
    /**
     * Creates a bridge to a {@link CompletableFuture} JDK API. The default implementation
     * ensures that {@link Future#cancel} and {@link CompletableFuture#complete} have no effect and
     * that {@link Future#get} waits for the task's completion. It is strongly recommended that
     * the returned object implements {@link Completable}, otherwise it will be wrapped before
     * returning to API clients.
     * <p>
     * The method may be called on any thread, even if a Future for the task already exists, and is called without
     * holding any processing locks on the task. The implementation must provide appropriate
     * synchronization. The returned value may be thrown away and an Future instance created by another
     * call to this method may be ultimately returned to the API client.
     * @param <T>
     * @return future instance.
     * @since 9.17
     */
    protected <T> Privileged<T> createFutureBridge() {
        FutureOverrides.CompletionStageFuture<T> fut;
        
        synchronized (this) {
            fut = new FutureOverrides.CompletionStageFuture<T>(RP) {
                @Override
                public T get() throws InterruptedException, ExecutionException {
                    waitFinished();
                    return super.get();
                }
                
                @Override
                public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                    if (timeout > 0) {
                        waitFinished(unit.toMillis(timeout));
                    }
                    return super.get(0, TimeUnit.MILLISECONDS);
                }
            };
        }
        return new Privileged<T>() {
            @Override
            public CompletionStageFuture<T> future() {
                return fut;
            }

            @Override
            public boolean cancel(boolean stopIfRunning) {
                return fut.superCancel(stopIfRunning);
            }

            @Override
            public boolean complete(T val) {
                return fut.superComplete(val);
            }

            @Override
            public boolean completeExceptionally(Throwable e) {
                return fut.superCompleteExceptionally(e);
            }
        };
    }
    
    /**
     * Converts this Task to a {@link CompletionStage} + {@link Future} implementation,
     * which completes at the time this Task completes. The returned API will refuse
     * {@link Future#cancel} requests. {@link Future#get} will behave as if {@link #waitFinished}
     * was called, but if the Task completes with an exception, {@link Future#get} will throw
     * an {@link ExecutionException}.
     * <p>
     * Additional {@link CompletionStage}s added to the returned Future 
     * run after TaskListeners.
     * <p>
     * Task subclasses may extend the semantics.
     * @return completable task.
     */
    public final Completable<Void> asFuture() {
        return asFuture(Void.class);
    }
    
    /**
     * Returns a Future that represents this task and its result. If the result is not
     * compatible with the passed type, the resulting Future will only report {@code null} from
     * its {@link Future#get} method. Tasks created without result always report {@code null}: the
     * value is not useful, except to know that the task has completed normally.
     * 
     * @param <T> type of the result
     * @param rt expected type
     * @return Future that represents this task.
     */
    public final <T> Completable<T> asFuture(Class<T> rt) {
        Privileged<T> p = getFutureInternal(true);
        if (rt == null || resultType == null) {
            return p.future();
        } else if (rt.isAssignableFrom(resultType)) {
            return p.future();
        } else {
            CompletionStage<T> ff = p.future().handle((Object o, Throwable t) -> {
                if (t != null) {
                    throw new CompletionException(t);
                } else if (o != null && resultType.isInstance(o)) {
                    return (T)o;
                } else {
                    return (T)null;
                }
            });
            return asCompletable(ff);
        }
    }
    
    /**
     * Convenience method, that creates a CompletableFuture that cannot be cancelled or completed. This
     * prevents clients from completing a task, the only one who can really complete is the caller that
     * stores the {@link Privileged} handle.
     * 
     * @param <T> result type
     * @param fut original Future
     * @return restricted Future
     */
    protected final <T> Privileged<T> restrictedFuture(CompletableFuture<T> fut, Executor asyncExecutor) {
        return new Privileged<T>() {
            @Override
            public CompletionStageDelegate<T> future() {
                return new CompletionStageDelegate<>(asyncExecutor == null ? RP : asyncExecutor, fut);
            }

            @Override
            public boolean cancel(boolean stopIfRunning) {
                return fut.cancel(stopIfRunning);
            }

            @Override
            public boolean complete(T val) {
                return fut.complete(val);
            }

            @Override
            public boolean completeExceptionally(Throwable e) {
                return fut.completeExceptionally(e);
            }
        };
    }
    
    /**
     * Provides privileged access to the Future exposed to the clients,
     * that allows to terminate the Future.
     * @return privileged object.
     */
    protected final <T> Privileged<T> getFutureInternal(boolean create) {
        CompletableFuture<T> f;
        synchronized (this) {
            if (futureBridge != null) {
                return (Privileged<T>)futureBridge;
            }
        }
        Privileged<T> priv = createFutureBridge();
        Throwable err;
        synchronized (this) {
            if (futureBridge == null) {
                futureBridge = priv;
            } else {
                return (Privileged<T>)futureBridge;
            }
            err = this.completedWithException;
        }
        if (isFinished()) {
            if (err != null) {
                priv.completeExceptionally(err);
            } else {
                priv.complete((T)resultValue);
            }
        }
        return (Privileged<T>)priv;
    }
    
    /**
     * Wraps the CompletableFuture into type-compatible delegation wrapper, if not
     * already Completable.
     * @param f the future
     * @return Completable instance
     */
    private final <T, R extends Future<T> & CompletionStage<T>> R asCompletable(CompletionStage f) {
        if (f instanceof Completable) {
            return (R)f;
        } else {
            return (R)new FutureOverrides.CompletionStageDelegate<>(preferredExecutor(), (CompletableFuture<T>)f);
        }
    }

    /**
     * Allows access to both {@link Future} and {@link CompletionStage} APIs. In addition,
     * it provides access to {@link CompletableFuture#getNow} API for convenience. Clients of
     * API prior to 9.17 may have typecasted the returned Future to a {@link FutureTask},
     * newer clients may typecast it to the {@link Completable}.
     * <p>
     * The class serves as a bridge between {@link Task}, {@link RequestProcessor#Task} and JDK
     * concurrent APIs. It allows code to work against {@link CompletionStage} API rather than
     * using {@link TaskListener}s, eliminating unnecessary dependencies on NetBeans APIs. {@link CompletionState}
     * also provides more facilities for task chaining and scheduling than the NetBeans native API.
     * <p>
     * The Completable will complete whenever its {@link Task} completes its pending run. 
     * <p>
     * <b>Warning: it is only safe</b> to use methods of Java 8 API's {@link CompletionStage}; newer
     * methods will not be properly delegated.
     *
     * @param <T> the Future's value type.
     * @since 9.17
     */
    public interface Completable<T> extends Future<T>, CompletionStage<T> {
        public T getNow(T missingValue);
    }
}
