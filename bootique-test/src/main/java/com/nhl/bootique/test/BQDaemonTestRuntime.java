package com.nhl.bootique.test;

import static org.junit.Assert.assertTrue;

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nhl.bootique.BQRuntime;
import com.nhl.bootique.Bootique;
import com.nhl.bootique.command.CommandOutcome;

/**
 * @since 0.13
 */
public class BQDaemonTestRuntime extends BQTestRuntime {

	private static final Logger LOGGER = LoggerFactory.getLogger(BQDaemonTestRuntime.class);

	private ExecutorService executor;
	private Function<BQRuntime, Boolean> startupCheck;

	private BQRuntime runtime;

	public BQDaemonTestRuntime(Consumer<Bootique> configurator, Function<BQRuntime, Boolean> startupCheck) {
		super(configurator);

		this.startupCheck = startupCheck;
		this.executor = Executors.newCachedThreadPool();
	}

	public void start(long timeout, TimeUnit unit, String... args)
			throws InterruptedException, ExecutionException, TimeoutException {

		start(args);

		Future<Boolean> startupFuture = executor.submit(() -> {

			try {
				while (!startupCheck.apply(runtime)) {
					LOGGER.info("daemon runtime hasn't started yet...");
					Thread.sleep(500);
				}

				return true;
			} catch (InterruptedException e) {
				LOGGER.warn("Timed out waiting for server to start");
				return false;
			} catch (Throwable th) {
				LOGGER.warn("Server error", th);
				return false;
			}

		});

		assertTrue(startupFuture.get(timeout, unit));
		LOGGER.info("Started successfully...");
	}

	protected void start(String... args) {
		this.runtime = createRuntime(args);
		this.executor.submit(() -> run());
	}

	public void stop() throws InterruptedException {
		executor.shutdownNow();
		executor.awaitTermination(3, TimeUnit.SECONDS);

		// TODO: ?
		// runtime.shutdown();
	}

	protected CommandOutcome run() {
		Objects.requireNonNull(runtime);
		try {
			return runtime.getRunner().run();
		} finally {
			runtime.shutdown();
		}
	}
}
