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

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import org.netbeans.junit.NbTestCase;

/**
 *
 * @author sdedic
 */
public class RequestProcessorFutureTest extends NbTestCase{
    private RequestProcessor theProcessor;
    
    private volatile String stage1;
    
    private volatile String stage2;
    
    private CountDownLatch start = new CountDownLatch(1);
    private CountDownLatch running = new CountDownLatch(1);

    
    public RequestProcessorFutureTest(String name) {
        super(name);
        theProcessor = new RequestProcessor(name, 1);
    }

    class S1 implements Runnable {
        final boolean before;

        public S1(boolean before) {
            this.before = before;
        }
        
        public void run() {
            if (before) {
                awaitStart();
            }
            stage1 = "1";
            if (!before) {
                // run after this task completes.
                theProcessor.post(() -> start.countDown());
            }
        }
    }
    
    private void awaitStart() {
        try {
            start.await();
        } catch (InterruptedException ex) {
            fail("Interrupted.");
        }
    }

    private volatile boolean interrupted;
    
    private void awaitStart2() {
        try {
            start.await();
        } catch (InterruptedException ex) {
            interrupted = true;
        }
    }
    
    public void testStageCompletesWithFutureBefore() throws Exception {
        checkStageCompletesWithFuture(true);
    }
    
    public void testStageCompletesWithFutureAfter() throws Exception {
        checkStageCompletesWithFuture(false);
    }
    
    private void checkStageCompletesWithFuture(boolean beforeTermination) throws Exception {
        Future<?> f = theProcessor.submit(new S1(beforeTermination));
        if (!beforeTermination) {
            awaitStart();
        }
        CompletionStage<?> cs = (CompletionStage<?>)f;
        CompletableFuture<?> cf = cs.toCompletableFuture();
        
        assertFalse(beforeTermination && cf.isDone());
        cs.thenAccept((v) -> stage2 = "2");
        
        if (beforeTermination) {
            start.countDown();
        }
        
        cf.get(100, TimeUnit.MILLISECONDS);
        assertTrue(f.isDone());
        assertTrue(cf.isDone());
        
        assertNotNull(stage1);
        assertNotNull(stage2);
    }
    
    private void checkStageCompletesWithException(boolean beforeTermination) throws Exception {
        Future<?> f = theProcessor.submit(new S1(beforeTermination) {
            public void run() {
                // throw an exception after setting stage:
                super.run();
                throw new IllegalStateException();
            }
        });
        if (!beforeTermination) {
            awaitStart();
        }
        CompletionStage<?> cs = (CompletionStage<?>)f;
        CompletableFuture<?> cf = cs.toCompletableFuture();
        
        assertFalse(beforeTermination && cf.isDone());
        cs.
            exceptionally((t) -> { exception = t; return null; }).
            thenAccept((v) -> stage2 = "2");
        
        if (beforeTermination) {
            start.countDown();
        }
        
//        cf.get(100, TimeUnit.MILLISECONDS);
        try {
            cf.get();
        } catch (ExecutionException ex) {
            assertTrue(ex.getCause() instanceof IllegalStateException);
        }
        assertFalse(f.isCancelled());
        assertTrue(f.isDone());
        assertTrue(cf.isDone());
        
        assertNotNull(stage1);
        assertTrue(exception instanceof IllegalStateException);
        assertEquals("2", stage2);
    }

    public void testStageCompletesExceptionallyBefore() throws Exception {
        checkStageCompletesWithException(true);
    }
    
    public void testStageCompletesExceptionallyAfter() throws Exception {
        checkStageCompletesWithException(false);
    }
    
    class S2 implements Runnable {
        private final boolean before;

        public S2(boolean before) {
            this.before = before;
        }
        
        
        public void run() {
            running.countDown();
            if (before) {
                awaitStart2();
            }
            if (interrupted) {
                return;
            }
            stage1 = "1";
        }
    }
    
    private Throwable exception;
    
    /**
     * Check that cancel on the submission result will cancel the returned or 
     * chained stages.
     * 
     * @throws Exception 
     */
    public void testStageCancelsWithFuture() throws Exception {
        Future<?> f = theProcessor.submit(new S2(true));
        CompletionStage<?> cs = (CompletionStage<?>)f;
        CompletableFuture<?> cf = cs.toCompletableFuture();
        
        CompletableFuture<?> never = cs.thenAccept((v) -> stage2 = "n").toCompletableFuture();
        CompletionStage<?> ex =  cf.exceptionally(
                (t) -> { 
                    exception = t; 
                    return null; 
                });
        CompletionStage<?> st = ex.
                thenAccept((v) -> 
                        stage2 = "2"
                );
        // start the task
        running.await();
        
        // and cancel it, without issuing an Thread.interrupt
        f.cancel(true);
        start.countDown();
        
        try {
            ex.toCompletableFuture().get();
            st.toCompletableFuture().get();
            f.get();
            fail("Should be cancelled.");
        } catch (CancellationException x) {
            // ok
        }
        Thread.sleep(100);
        assertTrue(f.isCancelled());
        assertTrue(cf.toCompletableFuture().isCompletedExceptionally());
        
        // direct dependent has completed as well
        assertTrue(never.isDone());
        assertTrue(never.isCompletedExceptionally());
        
        assertTrue(st.toCompletableFuture().isDone());
        // completed because of the 'exceptionally' function.
        assertFalse(ex.toCompletableFuture().isCompletedExceptionally());
        
        assertTrue(exception instanceof CancellationException);
        assertEquals("2", stage2);
        assertNull(stage1);
    }
    

    /**
     * Checks that future completes with RP task.
     * @throws Exception 
     */
    public void testFutureCompletesWithTask() throws Exception {
        
    }
    
    /**
     * Checks that RP.Task.cancel() will cancel task's future as well
     * @throws Exception 
     */
    public void testTaskCancelCancelsFuture() throws Exception {
        
    }
    
    /**
     * Checks that Future.cancel() will cancel the task.
     * @throws Exception 
     */
    public void testFutureCancelCancelsTask() throws Exception {
    }
    
}
