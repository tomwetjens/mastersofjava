package nl.moj.server.submit.service;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;
import nl.moj.server.assignment.model.Assignment;
import nl.moj.server.competition.model.CompetitionSession;
import nl.moj.server.runtime.model.AssignmentFile;
import nl.moj.server.submit.model.SourceMessage;
import nl.moj.server.teams.model.Team;
import nl.moj.server.user.model.User;

@Builder
@Data
public class SubmitRequest {

    private final Team team;
    private final Assignment assignment;
    private final CompetitionSession session;
    private final List<AssignmentFile> tests;
    private final Map<Path,String> sources;
    private final Duration timeElapsed;
    // TODO this should not be here
    private final boolean isSubmitAllowed;
}
