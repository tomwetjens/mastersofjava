package nl.moj.server.message.service;

import lombok.extern.slf4j.Slf4j;
import nl.moj.server.TaskControlController.TaskMessage;
import nl.moj.server.compiler.CompileResult;
import nl.moj.server.message.model.*;
import nl.moj.server.submit.SubmitResult;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@Slf4j
public class MessageService {

	private static final String DEST_COMPETITION = "/queue/competition";
	private static final String DEST_TESTRESULTS = "/queue/feedbackpage";
	private static final String DEST_START = "/queue/start";
	private static final String DEST_STOP = "/queue/stop";
	private static final String DEST_RANKINGS = "/queue/rankings";

	private SimpMessagingTemplate template;

	public MessageService(SimpMessagingTemplate template) {
		super();
		this.template = template;
	}

	public void sendTestFeedback(SubmitResult testResult) {
		testResult.getTestResults().forEach(tr -> {
			TestFeedbackMessage msg = TestFeedbackMessage.builder()
					.success(tr.isSuccessful())
					.team(tr.getTeam().getName())
					.test(tr.getTestName())
					.message(tr.getMessage())
					.build();
			log.info("Sending test results: {}", msg);
			template.convertAndSendToUser(msg.getTeam(), DEST_COMPETITION, msg);
			template.convertAndSend(DEST_TESTRESULTS, msg);
		});
	}

	public void sendSubmitFeedback(SubmitResult submitResult) {
		sendTestFeedback(submitResult);
		
		SubmitFeedbackMessage msg = SubmitFeedbackMessage.builder()
				.score(submitResult.getScore())
				.remainingSubmits(submitResult.getRemainingSubmits())
				.team(submitResult.getTeam().getName())
				.success(submitResult.isSuccess())
				.message("TODO")
				.build();

		log.info("Sending submit results: {}", msg);
		template.convertAndSendToUser(msg.getTeam(), DEST_COMPETITION, msg);
		template.convertAndSend(DEST_TESTRESULTS, msg);
		template.convertAndSend(DEST_RANKINGS, "refresh");
	}

	public void sendCompileFeedback(SubmitResult submitResult) {
		CompileResult result = submitResult.getCompileResult();
		CompileFeedbackMessage msg = CompileFeedbackMessage.builder()
				.success(result.isSuccessful())
				.team(result.getTeam().getName())
				.message(result.getMessage())
				.build();
		log.info("Sending compile results: {}", msg);
		template.convertAndSendToUser(msg.getTeam(), DEST_COMPETITION, msg);
	}

	public void sendStartToTeams(String taskname) {
		template.convertAndSend(DEST_START, taskname);
		template.convertAndSend(DEST_COMPETITION, StartAssignmentMessage.builder().assignment(taskname).build());
	}

	public void sendStopToTeams(String taskname) {
		template.convertAndSend(DEST_STOP, new TaskMessage(taskname));
		template.convertAndSend(DEST_COMPETITION, StopAssignmentMessage.builder().assignment(taskname).build());
	}

	public void sendRemainingTime(Long remainingTime, Long totalTime) {
		try {
			log.info("Sending remaining time: r={}, t={}", remainingTime, totalTime);
			TimerSyncMessage msg = TimerSyncMessage.builder()
					.remainingTime(remainingTime)
					.totalTime(totalTime)
					.build();
			template.convertAndSend(DEST_COMPETITION, msg);
			template.convertAndSend("/queue/time", msg);
		} catch (Exception e) {
			log.warn("Failed to send remaining time.", e);
		}
	}

}