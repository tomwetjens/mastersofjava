package nl.moj.server.compiler;

import lombok.extern.slf4j.Slf4j;
import nl.moj.server.config.properties.Languages;
import nl.moj.server.config.properties.MojServerProperties;
import nl.moj.server.runtime.CompetitionRuntime;
import nl.moj.server.runtime.model.AssignmentFile;
import nl.moj.server.runtime.model.AssignmentFileType;
import nl.moj.server.submit.model.SourceMessage;
import nl.moj.server.teams.model.Team;
import nl.moj.server.util.LengthLimitedOutputCatcher;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.zeroturnaround.exec.ProcessExecutor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CompileService {

	private Executor executor;

	private CompetitionRuntime competition;

	private MojServerProperties mojServerProperties;

	public CompileService(@Qualifier("compiling") Executor executor, CompetitionRuntime competition, MojServerProperties mojServerProperties) {
		this.executor = executor;
		this.competition = competition;
		this.mojServerProperties = mojServerProperties;
	}

	public CompletableFuture<CompileResult> compile(Team team, SourceMessage message) {
		return CompletableFuture.supplyAsync(compileTask(mojServerProperties.getLanguages().getJavaVersion(), team, message),
				executor);
	}

	private Supplier<CompileResult> compileTask(Languages.JavaVersion javaVersion, Team team, SourceMessage message) {
		return () -> {
			List<AssignmentFile> resources = getResourcesToCopy();

			List<AssignmentFile> assignmentFiles = getReadonlyAssignmentFilesToCompile();
			String assignment = competition.getCurrentAssignment().getAssignment().getName();
			// TODO this should be somewhere else
			File teamAssignmentDir = FileUtils.getFile(mojServerProperties.getDirectories().getBaseDirectory(),
					mojServerProperties.getDirectories().getTeamDirectory(), team.getName(), assignment );
			File sourcesDir = FileUtils.getFile(teamAssignmentDir, "sources");
			File classesDir = FileUtils.getFile(teamAssignmentDir, "classes");
			try {
				FileUtils.cleanDirectory(teamAssignmentDir);
				sourcesDir.mkdirs();
				classesDir.mkdirs();
			} catch (IOException e) {
				log.error("error while cleaning teamdir", e);
			}

			// TODO fix compile result so team knows something is very wrong.
			resources.forEach( r -> {
				try {
					FileUtils.copyFileToDirectory(r.getAbsoluteFile().toFile(), classesDir);
				} catch (IOException e) {
					log.error("error while writing resources to classes dir", e);
				}
			});

			// TODO fix compile result so team knows something is very wrong.
			message.getSources().forEach((uuid, v) -> {
				AssignmentFile orig = getOriginalAssignmentFile(uuid);
				File f = sourcesDir.toPath().resolve(orig.getFile()).toFile();
				try {
					FileUtils.writeStringToFile(f, v, StandardCharsets.UTF_8);
				} catch (IOException e) {
					log.error("error while writing sourcefiles to sources dir", e);
				}
				assignmentFiles.add(orig.toBuilder()
						.absoluteFile(f.toPath())
						.build());
			});
			// C) Java compiler options
			CompileResult compileResult = null;
			try {
				boolean timedOut = false;
				int exitvalue = 0;
				final LengthLimitedOutputCatcher compileOutput = new LengthLimitedOutputCatcher(mojServerProperties);
				final LengthLimitedOutputCatcher compileErrorOutput = new LengthLimitedOutputCatcher(mojServerProperties);
				try {
					List<String> cmd = new ArrayList<>();
					cmd.add(javaVersion.getCompiler().toString());
					cmd.add("-cp");
					cmd.add(makeClasspath(classesDir).stream().map(f -> f.getAbsoluteFile().toString()).collect(Collectors.joining(File.pathSeparator)));
					cmd.add("-Xlint:all");
					cmd.add("-g:source,lines,vars");
					cmd.add("-d");
					cmd.add(classesDir.getAbsoluteFile().toString());
					assignmentFiles.forEach(a -> {
						cmd.add(a.getAbsoluteFile().toString());
					});

					final ProcessExecutor jUnitCommand = new ProcessExecutor().command(cmd);
					log.debug("Executing command {}", String.join(" \\\n", cmd));
					exitvalue = jUnitCommand.directory(teamAssignmentDir)
							.timeout(mojServerProperties.getRuntimes().getCompile().getTimeout(), TimeUnit.SECONDS).redirectOutput(compileOutput)
							.redirectError(compileErrorOutput).execute().getExitValue();
				} catch (TimeoutException e) {
					// process is automatically destroyed
					log.debug("Compile timed out and got killed", team.getName());
					timedOut = true;
				} catch (SecurityException se) {
					log.error(se.getMessage(), se);
				}
				log.debug("exitValue {}", exitvalue);
				if (timedOut) {
					compileOutput.getBuffer().append('\n').append(mojServerProperties.getLimits().getUnitTestOutput().getTestTimeoutTermination());
				}

				final String result;
				// TODO can this be done nicer?
				if (compileOutput.length() > 0) {
					// if we still have some output left and exitvalue = 0
					if (compileOutput.length() > 0 && exitvalue == 0 && !timedOut) {
						compileResult = CompileResult.success(team);
					} else {
						stripTeamPathInfo(compileOutput.getBuffer(), FileUtils.getFile(teamAssignmentDir, "sources", assignment));
						compileResult = CompileResult.fail(team, compileOutput.toString());
					}
				} else {
					log.debug(compileOutput.toString());
					stripTeamPathInfo(compileErrorOutput.getBuffer(), FileUtils.getFile(teamAssignmentDir, "sources", assignment));
					result = compileErrorOutput.toString();
					if ((exitvalue == 0) && !timedOut) {
						compileResult = CompileResult.success(team);
					} else {
						compileResult = CompileResult.fail(team, result);
					}
				}
			} catch (Exception e) {
				log.error(e.getMessage(), e);
			}
			return compileResult;
		};
	}

	private AssignmentFile getOriginalAssignmentFile(String uuid) {
		return competition.getAssignmentState().getAssignmentFiles().stream()
				.filter( f -> f.getUuid().toString().equals(uuid)).findFirst().orElseThrow(() -> new RuntimeException("Could not find original assignment file for UUID " + uuid));
	}

	private void stripTeamPathInfo(StringBuilder result, File prefix) {
		final Matcher matcher = Pattern.compile("^(" + prefix.getAbsolutePath() + ")/?", Pattern.MULTILINE).matcher(result);
		if (matcher.find()) {
			for (int i = 0; i < matcher.groupCount(); i++) {
				log.debug("group {} = {}", i, matcher.group(i));
			}
			log.debug("stripped '{}', from {}", matcher.group(), result.toString());
			result.delete(0, matcher.end());
			if (result.length() > 0 && result.charAt(0) == '\n') {
				result.deleteCharAt(0);
			}
		}
	}

	private List<AssignmentFile> getReadonlyAssignmentFilesToCompile() {
		return competition.getAssignmentState().getAssignmentFiles()
				.stream()
				.filter(f -> f.getFileType() == AssignmentFileType.READONLY ||
						f.getFileType() == AssignmentFileType.TEST ||
						f.getFileType() == AssignmentFileType.HIDDEN_TEST)
				.collect(Collectors.toList());
	}

	private List<AssignmentFile> getResourcesToCopy() {
		return competition.getAssignmentState().getAssignmentFiles()
				.stream()
				.filter(f -> f.getFileType() == AssignmentFileType.RESOURCE ||
						f.getFileType() == AssignmentFileType.TEST_RESOURCE ||
						f.getFileType() == AssignmentFileType.HIDDEN_TEST_RESOURCE)
				.collect(Collectors.toList());
	}

	private List<File> makeClasspath(File classesDir) {
		final List<File> classPath = new ArrayList<>();
		classPath.add(classesDir);
		classPath.add(
				FileUtils.getFile(mojServerProperties.getDirectories().getBaseDirectory(), mojServerProperties.getDirectories().getLibDirectory(), "junit-4.12.jar"));
		classPath.add(FileUtils.getFile(mojServerProperties.getDirectories().getBaseDirectory(), mojServerProperties.getDirectories().getLibDirectory(),
				"hamcrest-all-1.3.jar"));
		for (File file : classPath) {
			if (!file.exists()) {
				log.error("not found: {}", file.getAbsolutePath());
			} else {
				log.trace("found: {}", file.getAbsolutePath());
			}
		}
		return classPath;
	}
}