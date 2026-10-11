package tomatopotato.mochaccino.client;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.Minecraft;

import tomatopotato.mochaccino.Mochaccino;

import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class Restarter {
	private static final String SCRIPT = """
		pid=%d
		echo "waiting for $pid to exit"
		n=0
		while [ $n -lt 200 ]; do
			state=$(ps -o stat= -p $pid 2>/dev/null)
			case "$state" in ''|Z*) break ;; esac
			n=$((n + 1))
			sleep 0.3
		done
		echo "starting: $*"
		if [ -x /usr/bin/perl ]; then
			exec /usr/bin/perl -MPOSIX -e 'POSIX::setsid(); exec @ARGV' "$@"
		fi
		exec "$@"
		""";

	private Restarter() {
	}

	public static void restart(Minecraft client) {
		ProcessHandle.Info info = ProcessHandle.current().info();
		if (info.command().isEmpty() || info.arguments().isEmpty()) {
			Mochaccino.LOGGER.warn("Cannot restart: the command line of this process isn't available");
			return;
		}

		List<String> command = new ArrayList<>();
		command.add("/bin/sh");
		command.add("-c");
		command.add(SCRIPT.formatted(ProcessHandle.current().pid()));
		command.add("sh");
		command.add(info.command().get());
		command.addAll(List.of(info.arguments().get()));

		File log = FabricLoader.getInstance().getGameDir().resolve("logs").resolve("mochaccino-restart.log").toFile();
		try {
			Files.createDirectories(log.toPath().getParent());
			Files.writeString(Path.of(log.getPath()), "Restart requested " + LocalDateTime.now() + "\n");
			new ProcessBuilder(command)
				.directory(new File("").getAbsoluteFile())
				.redirectInput(Redirect.from(new File("/dev/null")))
				.redirectOutput(Redirect.appendTo(log))
				.redirectErrorStream(true)
				.start();
		} catch (IOException e) {
			Mochaccino.LOGGER.warn("Could not start the new instance, staying open", e);
			return;
		}
		client.stop();
	}
}
