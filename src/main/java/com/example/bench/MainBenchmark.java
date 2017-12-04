/*
 * Copyright 2016-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.bench;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

@Measurement(iterations = 5)
@Warmup(iterations = 1)
@Fork(value = 2, warmups = 0)
@BenchmarkMode(Mode.SingleShotTime)
public class MainBenchmark {

	private static String[] DEFAULT_JVM_ARGS = new String[] { "-Xmx128m",
			"-Djava.security.egd=file:/dev/./urandom", "-XX:TieredStopAtLevel=1",
			"-noverify" };

	@Benchmark
	public void main(MainState state) throws Exception {
		state.run();
	}

	@State(Scope.Benchmark)
	public static class MainState extends ProcessLauncherState {

		public static enum Sample {
			base(false), norm(false, DEFAULT_JVM_ARGS), expl(true), fast(true,
					DEFAULT_JVM_ARGS), main(true, false), best(true, false,
							DEFAULT_JVM_ARGS), nost(true, false, new String[] {
									"--spring.cloud.stream.enabled=false",
									"--spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.integration.IntegrationAutoConfiguration,org.springframework.cloud.stream.config.codec.kryo.KryoCodecAutoConfiguration" },
									DEFAULT_JVM_ARGS);
			private boolean exploded = false;
			private boolean launcher = true;
			private String[] jvmArgs;
			private String[] args;

			private Sample(String... jvmArgs) {
				this(false, true, jvmArgs);
			}

			private Sample(boolean exploded, String... jvmArgs) {
				this(exploded, true, jvmArgs);
			}

			private Sample(boolean exploded, boolean launcher, String... jvmArgs) {
				this(exploded, launcher, new String[0], jvmArgs);
			}

			private Sample(boolean exploded, boolean launcher, String[] args,
					String[] jvmArgs) {
				this.launcher = launcher;
				this.exploded = exploded;
				this.args = args;
				this.jvmArgs = jvmArgs;
			}

		}

		@Param
		private Sample sample = Sample.fast;

		public MainState() {
			super("../java-function-invoker", "target/test");
		}

		@TearDown(Level.Iteration)
		public void stop() throws Exception {
			super.after();
		}

		@Setup(Level.Trial)
		public void start() throws Exception {
			if (sample.exploded) {
				setExploded();
			}
			if (!sample.launcher) {
				setApplicationMain();
			}
			jvmArgs(sample.jvmArgs);
			args(sample.args);
			super.before();
		}
	}

}
