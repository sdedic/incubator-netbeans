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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertSame;
import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;
import org.netbeans.junit.NbTestCase;
import org.openide.util.Task.Completable;

/**
 *
 * @author sdedic
 */
public class TaskFuturesTest extends NbTestCase {
    private Logger LOG;

    public TaskFuturesTest(String name) {
        super(name);
    }
    
    @Override
    protected void setUp() throws Exception {
        LOG = Logger.getLogger("org.openide.util.Task." + getName());
    }
    
    public void testCannotCancelTaskFuture() throws Exception {
        final CountDownLatch l = new CountDownLatch(1);
        Task task = new Task(new Runnable() {
            @Override
            public void run() {
                try {
                    l.await();
                } catch (InterruptedException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        });
        Thread thread = new Thread(task);
        thread.start();
        
        Future<Void> f = task.asFuture();
        assertFalse(f.isCancelled());
        assertFalse(f.isDone());
        
        f.cancel(true);
        assertFalse(f.isCancelled());
        assertFalse(f.isDone());

        l.countDown();
        f.get(200, TimeUnit.MILLISECONDS);
        f.cancel(true);
        assertTrue(f.isDone());
        assertFalse(f.isCancelled());
        
        assertFalse("Must not be able to cancel plain task: ", f.cancel(true));
    }
    
    public void testFutureGetCompletesWithTask() throws Exception {
        final CountDownLatch l = new CountDownLatch(1);
        Task task = new Task(new Runnable() {
            @Override
            public void run() {
                try {
                    l.await();
                } catch (InterruptedException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        });
        Thread thread = new Thread(task);
        thread.start();

        Future<Void> f = task.asFuture();
        assertFalse(f.isCancelled());
        assertFalse(f.isDone());
        l.countDown();
        f.get(200, TimeUnit.MILLISECONDS);
        assertTrue(f.isDone());
        assertNull(f.get());
    }
    
    public void testFutureGetThrowsOnException() throws Exception {
        final CountDownLatch l = new CountDownLatch(1);
        Task task = new Task(new Runnable() {
            @Override
            public void run() {
                try {
                    l.await();
                } catch (InterruptedException ex) {
                    Exceptions.printStackTrace(ex);
                }
                throw new IllegalStateException();
            }
        });
        Thread thread = new Thread(task);
        thread.start();

        Future<Void> f = task.asFuture();
        assertFalse(f.isDone());

        l.countDown();
        Thread.sleep(200);
        ExecutionException ee = null;
        try {
            f.get(200, TimeUnit.MILLISECONDS);
            fail("Should throw an exception");
        } catch (ExecutionException ex) {
            ee = ex;
        }
        assertNotNull(ee);
        assertTrue(ee.getCause() instanceof IllegalStateException);
        assertTrue(f.isDone());
        
        try {
            f.get();
            fail("Should throw an exception");
        } catch (ExecutionException ex) {
            assertSame(ee.getCause(), ex.getCause());
        }
    }
    
    /**
     * Checks that get(0) returns immediately, or throws a TimeoutException
     */
    public void testFutureTimedGetZero() throws Exception {
        final CountDownLatch l = new CountDownLatch(1);
        final CountDownLatch l2 = new CountDownLatch(1);
        Task task = new Task(new Runnable() {
            @Override
            public void run() {
                try {
                    l.await();
                } catch (InterruptedException ex) {
                    Exceptions.printStackTrace(ex);
                }
                l2.countDown();
            }
        });
        Thread thread = new Thread(task);
        thread.start();
        
        task.asFuture().getNow(null);
        // must not report finished
        assertFalse("Still not finished", task.isFinished());
        
        l.countDown();
        l2.await(200, TimeUnit.MILLISECONDS);
        assertTrue ("Should be finished", task.isFinished());
    }
    
    private String stage1;
    private String stage2;
    private String stage3;
    
    private CountDownLatch start = new CountDownLatch(1);
    private CountDownLatch last = new CountDownLatch(1);
    
    private void awaitStart() {
        try {
            start.await();
        } catch (InterruptedException ex) {
            throw new CompletionException(ex);
        }
    }
    
    public void testSynchronousCompletionStageBeforeRun() throws Exception {
        Task task = new Task(new Runnable() {
            @Override
            public void run() {
                awaitStart();
                stage1 = "1";
            }
        });
        Thread thread = new Thread(task);
        thread.start();
        
        Task.Completable<Void> f = task.asFuture();
        CompletableFuture<Void> res = f.thenAccept((v) -> stage2 = "2").toCompletableFuture();

        assertFalse(res.isDone());
        
        start.countDown();
        res.get(100, TimeUnit.MILLISECONDS);
        
        assertTrue(res.isDone());
        assertEquals("2", stage2);
    }

    public void testSynchronousCompletionStageWhenRunning() throws Exception {
        Task task = new Task(new Runnable() {
            @Override
            public void run() {
                stage1 = "1";
                last.countDown();
                awaitStart();
            }
        });
        Thread thread = new Thread(task);
        thread.start();
        
        last.await();
        
        Task.Completable<Void> f = task.asFuture();
        CompletableFuture<Void> res = f.thenAccept((v) -> stage2 = "2").toCompletableFuture();

        assertFalse(res.isDone());
        
        start.countDown();
        res.get(100, TimeUnit.MILLISECONDS);
        
        assertTrue(res.isDone());
        assertEquals("2", stage2);
    }
        
    public void testSynchronousCompletionStageAfterCompletion() throws Exception {
        Task task = new Task(new Runnable() {
            @Override
            public void run() {
                awaitStart();
                stage1 = "1";
            }
        });
        Thread thread = new Thread(task);
        thread.start();
        
        start.countDown();
        
        task.waitFinished();
        Task.Completable<Void> f = task.asFuture();
        CompletableFuture ff = f.thenAccept((v) -> stage2 = "2").toCompletableFuture();
        
        assertTrue(ff.isDone());
        assertEquals("2", stage2);
    }
    
    public void testExceptionalCompletionBefore() throws Exception {
        Task task = new Task(new Runnable() {
            @Override
            public void run() {
                awaitStart();
                stage1 = "1";
                throw new RuntimeException();
            }
        });
        Thread thread = new Thread(task);
        thread.start();
        
        CompletableFuture<Void> fut = task.asFuture().toCompletableFuture();
        fut.exceptionally((t) -> { stage2 = "2"; return null; }).
                thenAccept((v) -> stage3 = "3");
        
        start.countDown();
        
        try {
            fut.get();
            fail("Worker should have thrown an exception");
        } catch (ExecutionException ex) {
        }
        
        assertEquals("2", stage2);
        assertEquals("3", stage3);
    }
    
    public void testExceptionalCompletionAfter() throws Exception {
        Task task = new Task(new Runnable() {
            @Override
            public void run() {
                stage1 = "1";
                throw new RuntimeException();
            }
        });
        Thread thread = new Thread(task);
        thread.start();
        
        task.waitFinished();
        
        CompletableFuture<Void> fut = task.asFuture().toCompletableFuture();
        CompletableFuture<Void> res = fut.exceptionally((t) -> { stage2 = "2"; return null; }).
                thenAccept((v) -> stage3 = "3");
        
        start.countDown();
        
        try {
            fut.get();
            fail("Worker should have thrown an exception");
        } catch (ExecutionException ex) {
        }
        try {
            res.get(100, TimeUnit.MILLISECONDS);
            fail("The future should never complete normally");
        } catch (TimeoutException ex) {
            
        }
        assertEquals("2", stage2);
        assertEquals(null, stage3);
    }
}
