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
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Wraps a CompletableFuture instance and attempts to provide the default
 * executor.
 *
 * @author sdedic
 */
class FutureOverrides {
    
    static class FutureBase<T> extends CompletableFuture<T> implements Task.Completable<T> {
        protected final Executor commonExecutor;

        public FutureBase(Executor commonExecutor) {
            this.commonExecutor = commonExecutor;
        }
        
        protected final Executor commonExecutor() {
            return commonExecutor;
        }
        
        protected <T> CompletableFuture<T> wrap(CompletableFuture<T> original) {
            return createDelegate(original);
        }

        protected <T> FutureDelegate<T> createDelegate(CompletableFuture<T> original) {
            return new FutureDelegate<>(commonExecutor, original);
        }

        @Override
        public <U> CompletableFuture<U> thenApply(Function<? super T, ? extends U> fn) {
            return wrap(super.thenApply(fn));
        }

        @Override
        public <U> CompletableFuture<U> thenApplyAsync(Function<? super T, ? extends U> fn) {
            return wrap(super.thenApplyAsync(fn, commonExecutor));
        }

        @Override
        public <U> CompletableFuture<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor) {
            return wrap(super.thenApplyAsync(fn, executor));
        }

        @Override
        public CompletableFuture<Void> thenAccept(Consumer<? super T> action) {
            return wrap(super.thenAccept(action));
        }

        @Override
        public CompletableFuture<Void> thenAcceptAsync(Consumer<? super T> action) {
            return wrap(super.thenAcceptAsync(action, commonExecutor));
        }

        @Override
        public CompletableFuture<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor) {
            return wrap(super.thenAcceptAsync(action, executor));
        }

        @Override
        public CompletableFuture<Void> thenRun(Runnable action) {
            return wrap(super.thenRun(action));
        }

        @Override
        public CompletableFuture<Void> thenRunAsync(Runnable action) {
            return wrap(thenRunAsync(action, commonExecutor));
        }

        @Override
        public CompletableFuture<Void> thenRunAsync(Runnable action, Executor executor) {
            return wrap(super.thenRunAsync(action, executor));
        }

        @Override
        public <U, V> CompletableFuture<V> thenCombine(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
            return wrap(super.thenCombine(other, fn));
        }

        @Override
        public <U, V> CompletableFuture<V> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
            return wrap(super.thenCombineAsync(other, fn, commonExecutor));
        }

        @Override
        public <U, V> CompletableFuture<V> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn, Executor executor) {
            return wrap(super.thenCombineAsync(other, fn, executor));
        }

        @Override
        public <U> CompletableFuture<Void> thenAcceptBoth(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
            return wrap(super.thenAcceptBoth(other, action));
        }

        @Override
        public <U> CompletableFuture<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
            return wrap(thenAcceptBothAsync(other, action, commonExecutor));
        }

        @Override
        public <U> CompletableFuture<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action, Executor executor) {
            return wrap(super.thenAcceptBothAsync(other, action, executor));
        }

        @Override
        public CompletableFuture<Void> runAfterBoth(CompletionStage<?> other, Runnable action) {
            return wrap(super.runAfterBoth(other, action));
        }

        @Override
        public CompletableFuture<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action) {
            return wrap(runAfterBothAsync(other, action, commonExecutor));
        }

        @Override
        public CompletableFuture<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action, Executor executor) {
            return wrap(super.runAfterBothAsync(other, action, executor));
        }

        @Override
        public <U> CompletableFuture<U> applyToEither(CompletionStage<? extends T> other, Function<? super T, U> fn) {
            return wrap(super.applyToEither(other, fn));
        }

        @Override
        public <U> CompletableFuture<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn) {
            return wrap(applyToEitherAsync(other, fn, commonExecutor));
        }

        @Override
        public <U> CompletableFuture<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn, Executor executor) {
            return wrap(super.applyToEitherAsync(other, fn, executor));
        }

        @Override
        public CompletableFuture<Void> acceptEither(CompletionStage<? extends T> other, Consumer<? super T> action) {
            return wrap(super.acceptEither(other, action));
        }

        @Override
        public CompletableFuture<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action) {
            return wrap(acceptEitherAsync(other, action, commonExecutor));
        }

        @Override
        public CompletableFuture<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action, Executor executor) {
            return wrap(super.acceptEitherAsync(other, action, executor));
        }

        @Override
        public CompletableFuture<Void> runAfterEither(CompletionStage<?> other, Runnable action) {
            return wrap(super.runAfterEither(other, action));
        }

        @Override
        public CompletableFuture<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action) {
            return wrap(runAfterEitherAsync(other, action, commonExecutor));
        }

        @Override
        public CompletableFuture<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action, Executor executor) {
            return wrap(super.runAfterEitherAsync(other, action, executor));
        }

        @Override
        public <U> CompletableFuture<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn) {
            return wrap(super.thenCompose(fn));
        }

        @Override
        public <U> CompletableFuture<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn) {
            return wrap(thenComposeAsync(fn, commonExecutor));
        }

        @Override
        public <U> CompletableFuture<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, Executor executor) {
            return wrap(super.thenComposeAsync(fn, executor));
        }

        @Override
        public CompletableFuture<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
            return wrap(super.whenComplete(action));
        }

        @Override
        public CompletableFuture<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action) {
            return wrap(whenCompleteAsync(action, commonExecutor));
        }

        @Override
        public CompletableFuture<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, Executor executor) {
            return wrap(super.whenCompleteAsync(action, executor));
        }

        @Override
        public <U> CompletableFuture<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
            return wrap(super.handle(fn));
        }

        @Override
        public <U> CompletableFuture<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn) {
            return wrap(handleAsync(fn, commonExecutor));
        }

        @Override
        public <U> CompletableFuture<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, Executor executor) {
            return wrap(super.handleAsync(fn, executor));
        }

        @Override
        public CompletableFuture<T> toCompletableFuture() {
            return this;
        }

        @Override
        public CompletableFuture<T> exceptionally(Function<Throwable, ? extends T> fn) {
            return wrap(super.exceptionally(fn));
        }
    }

    static class FutureDelegate<T> extends FutureBase<T> {
        private final CompletableFuture<T> delegate;

        public FutureDelegate(Executor rpExecutor, CompletableFuture<T> delegate) {
            super(rpExecutor);
            this.delegate = delegate;
        }

        CompletableFuture<T> original() {
            return delegate;
        }

        @Override
        protected <T> FutureDelegate<T> createDelegate(CompletableFuture<T> original) {
            return new FutureDelegate<>(commonExecutor, original);
        }

        @Override
        public T getNow(T missingValue) {
            return original().getNow(missingValue);
        }
        
        @Override
        public boolean isDone() {
            return original().isDone();
        }

        @Override
        public T get() throws InterruptedException, ExecutionException {
            return original().get();
        }

        @Override
        public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return original().get(timeout, unit);
        }

        @Override
        public T join() {
            return original().join();
        }

        @Override
        public boolean complete(T value) {
            return original().complete(value);
        }

        @Override
        public boolean completeExceptionally(Throwable ex) {
            return original().completeExceptionally(ex);
        }

        @Override
        public <U> CompletableFuture<U> thenApply(Function<? super T, ? extends U> fn) {
            return wrap(original().thenApply(fn));
        }

        @Override
        public <U> CompletableFuture<U> thenApplyAsync(Function<? super T, ? extends U> fn) {
            return wrap(original().thenApplyAsync(fn, commonExecutor));
        }

        @Override
        public <U> CompletableFuture<U> thenApplyAsync(Function<? super T, ? extends U> fn, Executor executor) {
            return wrap(original().thenApplyAsync(fn, executor));
        }

        @Override
        public CompletableFuture<Void> thenAccept(Consumer<? super T> action) {
            return wrap(original().thenAccept(action));
        }

        @Override
        public CompletableFuture<Void> thenAcceptAsync(Consumer<? super T> action) {
            return wrap(original().thenAcceptAsync(action, commonExecutor));
        }

        @Override
        public CompletableFuture<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor) {
            return wrap(original().thenAcceptAsync(action, executor));
        }

        @Override
        public CompletableFuture<Void> thenRun(Runnable action) {
            return wrap(original().thenRun(action));
        }

        @Override
        public CompletableFuture<Void> thenRunAsync(Runnable action, Executor executor) {
            return wrap(original().thenRunAsync(action, executor));
        }

        @Override
        public <U, V> CompletableFuture<V> thenCombine(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
            return wrap(original().thenCombine(other, fn));
        }

        @Override
        public <U, V> CompletableFuture<V> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn) {
            return wrap(super.thenCombineAsync(other, fn, commonExecutor));
        }

        @Override
        public <U, V> CompletableFuture<V> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super T, ? super U, ? extends V> fn, Executor executor) {
            return wrap(original().thenCombineAsync(other, fn, executor));
        }

        @Override
        public <U> CompletableFuture<Void> thenAcceptBoth(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action) {
            return wrap(original().thenAcceptBoth(other, action));
        }

        @Override
        public <U> CompletableFuture<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, BiConsumer<? super T, ? super U> action, Executor executor) {
            return wrap(original().thenAcceptBothAsync(other, action, executor));
        }

        @Override
        public CompletableFuture<Void> runAfterBoth(CompletionStage<?> other, Runnable action) {
            return wrap(original().runAfterBoth(other, action));
        }

        @Override
        public CompletableFuture<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action, Executor executor) {
            return wrap(original().runAfterBothAsync(other, action, executor));
        }

        @Override
        public <U> CompletableFuture<U> applyToEither(CompletionStage<? extends T> other, Function<? super T, U> fn) {
            return wrap(original().applyToEither(other, fn));
        }

        @Override
        public <U> CompletableFuture<U> applyToEitherAsync(CompletionStage<? extends T> other, Function<? super T, U> fn, Executor executor) {
            return wrap(original().applyToEitherAsync(other, fn, executor));
        }

        @Override
        public CompletableFuture<Void> acceptEither(CompletionStage<? extends T> other, Consumer<? super T> action) {
            return wrap(original().acceptEither(other, action));
        }

        @Override
        public CompletableFuture<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action) {
            return wrap(acceptEitherAsync(other, action, commonExecutor));
        }

        @Override
        public CompletableFuture<Void> acceptEitherAsync(CompletionStage<? extends T> other, Consumer<? super T> action, Executor executor) {
            return wrap(original().acceptEitherAsync(other, action, executor));
        }

        @Override
        public CompletableFuture<Void> runAfterEither(CompletionStage<?> other, Runnable action) {
            return wrap(original().runAfterEither(other, action));
        }

        @Override
        public CompletableFuture<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action, Executor executor) {
            return wrap(original().runAfterEitherAsync(other, action, executor));
        }

        @Override
        public <U> CompletableFuture<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn) {
            return wrap(original().thenCompose(fn));
        }

        @Override
        public <U> CompletableFuture<U> thenComposeAsync(Function<? super T, ? extends CompletionStage<U>> fn, Executor executor) {
            return wrap(original().thenComposeAsync(fn, executor));
        }

        @Override
        public CompletableFuture<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
            return wrap(original().whenComplete(action));
        }

        @Override
        public CompletableFuture<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action, Executor executor) {
            return wrap(original().whenCompleteAsync(action, executor));
        }

        @Override
        public <U> CompletableFuture<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
            return wrap(original().handle(fn));
        }

        @Override
        public <U> CompletableFuture<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn, Executor executor) {
            return wrap(super.handleAsync(fn, executor));
        }

        @Override
        public CompletableFuture<T> toCompletableFuture() {
            return this;
        }

        @Override
        public CompletableFuture<T> exceptionally(Function<Throwable, ? extends T> fn) {
            return wrap(original().exceptionally(fn));
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return original().cancel(mayInterruptIfRunning);
        }

        @Override
        public boolean isCancelled() {
            return original().isCancelled();
        }

        @Override
        public boolean isCompletedExceptionally() {
            return original().isCompletedExceptionally();
        }

        @Override
        public void obtrudeValue(T value) {
            original().obtrudeValue(value);
        }

        @Override
        public void obtrudeException(Throwable ex) {
            original().obtrudeException(ex);
        }

        @Override
        public int getNumberOfDependents() {
            return original().getNumberOfDependents();
        }

        @Override
        public String toString() {
            return original().toString();
        }
    }
    
    static class CompletionStageFuture<T> extends FutureBase<T> {
        public CompletionStageFuture(Executor commonExecutor) {
            super(commonExecutor);
        }
        
        boolean superCancel(boolean stopIfRunning) {
            return super.cancel(stopIfRunning);
        }
        
        boolean superComplete(T value) {
            return super.complete(value);
        }

        boolean superCompleteExceptionally(Throwable ex) {
            return super.completeExceptionally(ex);
        }
        
        @Override
        public boolean cancel(boolean b) {
            return isCancelled();
        }

        @Override
        public boolean complete(T value) {
            return false;
        }

        @Override
        public boolean completeExceptionally(Throwable ex) {
            return false;
        }
        
        // forward compatible with JDK9; denies asynchronous completion by other
        // clients than the owner. As if this Future was completed before the Supplier
        // has executed.
        public CompletableFuture<T> completeAsync​(Supplier<? extends T> supplier, Executor executor) {
            return this;
        }
    }
    
    static class CompletionStageDelegate<T> extends FutureDelegate<T> {
        public CompletionStageDelegate(Executor rpExecutor, CompletableFuture<T> delegate) {
            super(rpExecutor, delegate);
        }
        
        @Override
        public boolean cancel(boolean stopIfRunning) {
            return super.isCancelled();
        }
        
        boolean superCancel(boolean stopIfRunning) {
            return super.cancel(stopIfRunning);
        }
        
        boolean superComplete(T value) {
            return super.complete(value);
        }

        boolean superCompleteExceptionally(Throwable ex) {
            return super.completeExceptionally(ex);
        }

        @Override
        public boolean complete(T value) {
            return false;
        }

        @Override
        public boolean completeExceptionally(Throwable ex) {
            return false;
        }

        // forward compatible with JDK9; denies asynchronous completion by other
        // clients than the owner.
        public CompletableFuture<T> completeAsync​(Supplier<? extends T> supplier, Executor executor) {
            return this;
        }
    }
}
