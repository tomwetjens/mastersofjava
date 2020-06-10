/*
   Copyright 2020 First Eight BV (The Netherlands)
 

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file / these files except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package nl.moj.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import nl.moj.server.assignment.descriptor.AssignmentDescriptor;
import nl.moj.server.assignment.model.Assignment;
import nl.moj.server.assignment.repository.AssignmentRepository;
import nl.moj.server.assignment.service.AssignmentService;
import nl.moj.server.assignment.service.AssignmentServiceException;
import nl.moj.server.competition.model.Competition;
import nl.moj.server.competition.model.CompetitionSession;
import nl.moj.server.competition.repository.CompetitionRepository;
import nl.moj.server.competition.repository.CompetitionSessionRepository;
import nl.moj.server.competition.service.CompetitionCleaningService;
import nl.moj.server.competition.service.CompetitionService;
import nl.moj.server.competition.service.GamemasterTableComponents;
import nl.moj.server.config.properties.MojServerProperties;
import nl.moj.server.rankings.model.Ranking;
import nl.moj.server.rankings.service.RankingsService;
import nl.moj.server.runtime.AssignmentRuntime;
import nl.moj.server.runtime.CompetitionRuntime;
import nl.moj.server.runtime.model.ActiveAssignment;
import nl.moj.server.runtime.model.AssignmentStatus;
import nl.moj.server.runtime.repository.AssignmentStatusRepository;
import nl.moj.server.teams.model.Role;
import nl.moj.server.teams.model.Team;
import nl.moj.server.teams.repository.TeamRepository;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class TaskControlController {

    private static final Logger log = LoggerFactory.getLogger(TaskControlController.class);

    private final MojServerProperties mojServerProperties;

    private final CompetitionRuntime competition;

    private final AssignmentService assignmentService;

    private final AssignmentRepository assignmentRepository;

    private final CompetitionRepository competitionRepository;

    private final TeamRepository teamRepository;

    private final AssignmentStatusRepository assignmentStatusRepository;

    private final AssignmentRuntime assignmentRuntime;

    private final GamemasterTableComponents gamemasterTableComponents;

    private final CompetitionSessionRepository competitionSessionRepository;

    private final CompetitionCleaningService competitionCleaningService;

    private final RankingsService rankingsService;

    private final CompetitionService competitionService;

    @ModelAttribute(name = "sessions")
    public List<CompetitionSession> sessions() {
        return competition.getSessions();
    }



    @ModelAttribute(name = "locationList")
    public List<File> locationList() {
        return competitionService.locationList();
    }

    @ModelAttribute(name = "teams")
    public List<Team> team() {
        return teamRepository.findAll();
    }

    @MessageMapping("/control/starttask")
    public void startTask(TaskMessage message) {
        competition.startAssignment(message.getTaskName());
    }

    @MessageMapping("/control/stoptask")
    public void stopTask() {
        competition.stopCurrentAssignment();
    }

    @MessageMapping("/control/clearCurrentAssignment")
    @SendToUser("/queue/controlfeedback")
    public String clearCompetition() {
        log.warn("clearCompetition entered");
        gamemasterTableComponents.deleteCurrentSessionResources();
        return competitionCleaningService.doCleanComplete();
    }

    @MessageMapping("/control/pauseResume")
    @SendToUser("/queue/controlfeedback")
    public String pauseResume() {
        log.warn("pauseResume entered");
        ActiveAssignment state = competition.getActiveAssignment();
        if (state==null|| state.getAssignment()==null) {
            return "no active assignment";
        }
        String name = state.getAssignment().getName();
        assignmentRuntime.pauseResume();
        if (assignmentRuntime.isPaused()) {
            return "assignment '"+name+"' paused, reloading page";
        } else {
            return "assignment '"+name+"' running, reloading page";
        }
    }


    @MessageMapping("/control/updateSettingRegistration")
    @SendToUser("/queue/controlfeedback")
    public String updateSettingRegistration(TaskMessage message) {
        boolean isDisable = "true".equals(message.value);
        log.info("update.isDisable " +isDisable + " val " + message.value);
        competitionService.setRegistrationFormDisabled(isDisable);
        if (isDisable) {
            return "registration form is disabled, users cannot register";
        } else {
            return "registration form is enabled, users can register";
        }
    }
    @MessageMapping("/control/restartAssignment")
    @SendToUser("/queue/controlfeedback")
    public String restartAssignment(TaskMessage message) {
        log.warn("restartAssignment entered: " +message.taskName);
        competition.getCompetitionState().getCompletedAssignments().clear();
        ActiveAssignment state = competition.getActiveAssignment();
        boolean isStopCurrentAssignment=state!=null && state.getAssignment()!=null && state.getAssignment().getName().equals(message.taskName);

        log.warn("isStopCurrentAssignment " + isStopCurrentAssignment);

        if (isStopCurrentAssignment) {
            competition.stopCurrentAssignment();
        }
        Assignment assignment = assignmentRepository.findByName(message.taskName);
        List<AssignmentStatus> ready4deletionList = assignmentStatusRepository.findByAssignmentAndCompetitionSession(assignment, competition.getCompetitionSession());

        log.warn("ready4deletionList " + ready4deletionList.size());
        if (ready4deletionList.isEmpty()) {
            return "Assignment not started yet: "  + message.taskName;
        }
        for (AssignmentStatus status: ready4deletionList) {
            assignmentStatusRepository.deleteById(status.getId());// correct cleaning: first delete all status items, afterwards delete all results
        }
        return "Assignment resetted: " + message.taskName + ", reload page";
    }


    @MessageMapping("/control/competitionSaveName")
    public void doCompetitionSaveName(TaskControlController.TaskMessage message)  throws JsonProcessingException {
        log.info("doCompetitionSaveName " + message + " " + message.getValue() );

        UUID uuid = UUID.fromString(message.getUuid());
        Competition competition =  competitionRepository.findByUuid(uuid);
        String[] parts = competition.getName().split("\\|");
        String input = message.getValue();
        if (parts.length>=2) {
            input = message.getValue() + "|" +parts[1];
        }
        competition.setName(input );
        competitionRepository.save(competition);

    }
    @MessageMapping("/control/competitionDelete")
    @SendToUser("/queue/controlfeedback")
   // @Transactional
    public String deleteCompetition(TaskControlController.TaskMessage message) {
        boolean isUpdateCurrentCompetition =  message.getUuid().equals(competition.getCompetition().getUuid());
        log.info("deleteCompetition " +message.getUuid() + " isUpdateCurrentCompetition " +isUpdateCurrentCompetition);

        try {
            long startAmount = competitionRepository.count();
            Competition competitionToClean = competitionRepository.findByUuid(UUID.fromString(message.getUuid()));
            List<CompetitionSession> sessionsToDelete = competitionSessionRepository.findByCompetition(competitionToClean);
            log.info("sessionsToDelete " +sessionsToDelete.size());
            if (isUpdateCurrentCompetition) {
                clearCompetition();
            }
            for (CompetitionSession session: sessionsToDelete) {
                competitionSessionRepository.delete(session);
            }
            if (startAmount>1) {

                competitionRepository.delete(competitionToClean);
                if (isUpdateCurrentCompetition) {
                    List<Competition> list = competitionRepository.findAll();

                    competition.loadMostRecentSession(list.get(0));
                }
            } else {
                competition.startSession(competitionToClean); // always guarantee at least one competition.
            }


        } catch (Exception ex) {
            log.info("Error during deletion", ex);
            return "Error during deletion";
        }
        return "Deleted competition, now reloading page";
    }

    @MessageMapping("/control/competitionCreateNew")
    @SendToUser("/queue/controlfeedback")
    public String doCompetitionCreateNew(TaskControlController.TaskMessage message)  throws JsonProcessingException {
        log.info("doCompetitionCreateNew " + message + " " + message.getValue() );
        if (StringUtils.isBlank(message.value)) {
            return "Please provide a valid name. ";
        }
        Competition newCompetition = new Competition();
        newCompetition.setUuid(UUID.randomUUID());
        newCompetition.setName(message.getValue());
        Competition registeredCompetition = competitionRepository.save(newCompetition);
        registeredCompetition.setAssignments(assignmentRepository.findAll()
                .stream()
                .map(competitionService.createOrderedAssignments(registeredCompetition))
                .collect(Collectors.toList()));
        Competition playableCompetition = competitionRepository.save(registeredCompetition);

        competition.startSession(playableCompetition);
        return "New competition created, reloading page";
    }
    @MessageMapping("/control/updateTeamStatus")
    @SendToUser("/queue/controlfeedback")
    public String doUpdateUserStatus(TaskControlController.TaskMessage message)  throws JsonProcessingException {
        log.info("updateUserStatus " + message + " " + message.getValue() );
        if (StringUtils.isBlank(message.uuid)) {
            return "Please provide valid input.";
        }
        Team team = teamRepository.findByUuid(UUID.fromString(message.getUuid()));
        if (team==null) {
            return "Team already deleted.";
        }
        UserStatusUpdate updateType = UserStatusUpdate.readUpdateType(message.getValue());
        if (!updateType.isAllowedToPlay) {
            // anonymous users cannot login anymore, via import files one can be activated again.
            team.setRole(Role.ANONYMOUS);
        }
        team.setTeamStatus(message.value);
        teamRepository.save(team);
        return updateType.value + " team '"+team.getName()+"'";
    }

    private enum UserStatusUpdate {
        DEFAULT ("Updated team", true),
        DISQUALIFY("Disqualified team", false),
        ARCHIVE("Archived team", false);
        private String value;
        private boolean isAllowedToPlay;
        UserStatusUpdate(String value, boolean isAllowedToPlay) {
            this.value = value;
            this.isAllowedToPlay = isAllowedToPlay;
        }
        public static UserStatusUpdate readUpdateType(String value) {
            UserStatusUpdate type = DEFAULT;
            if (DISQUALIFY.name().equals(value)) {
                type = DISQUALIFY;
            } else
            if (ARCHIVE.name().equals(value)) {
                type = ARCHIVE;
            }
            return type;
        }
    }

    /**
     * import assignments from a selected available year.
     * @param message
     * @return
     */
    @MessageMapping("/control/scanAssignments")
    @SendToUser("/queue/controlfeedback")
    public String cloneAssignmentsRepo(TaskMessage message) {
        try {
            Path path = mojServerProperties.getAssignmentRepo();
            if (StringUtils.isNumeric(message.taskName)) {
                path = competitionService.getLocationByYear(Integer.parseInt(message.taskName)).toPath();
            }
            if (!path.toFile().isDirectory()) {
                return "Assignment location invalid ("+path+").";
            }
            log.info("year " + message.taskName + " path " + path + " " +StringUtils.isNumeric(message.taskName)) ;
            List<Assignment> assignmentList = assignmentService.updateAssignments(path);
            if (assignmentList.isEmpty()) {
                return "No assignments scanned from location of "+path.toFile().getName()+" (improve assignments before importing).";
            }
            log.info("assignmentList " +assignmentList.size() + " " + assignmentList) ;

            String name = competition.getCompetition().getName().split("\\|")[0]+ "|" + path.toFile().getName();

            startCompetitionWithFreshAssignments(name);

            return "Assignments scanned from location "+path+" ("+assignmentList.size()+"), reloading to show them.";
        } catch (AssignmentServiceException ase) {
            log.error("Scanning assignments failed.", ase);
            return ase.getMessage();
        }
    }
    private void startCompetitionWithFreshAssignments(String name) {
        Competition resetCompetition = competition.getCompetition();

        resetCompetition.setName(name);
        // wipe previous assignments
        resetCompetition.setAssignments(new ArrayList<>());

        Competition startCompetition = competitionRepository.save(resetCompetition);

        Assert.isTrue( startCompetition.getAssignments().isEmpty(),"competition should have no assignments");
        // re-add updated assignments
        startCompetition.setAssignments(assignmentRepository.findAll()
                .stream()
                .map(competitionService.createOrderedAssignments(startCompetition))
                .collect(Collectors.toList()));
        startCompetition= competitionRepository.save(startCompetition);
        log.info("assignment repository " +startCompetition.getAssignments().size() + " " + startCompetition.getAssignmentsInOrder().size()) ;

        competition.loadSession(startCompetition, competition.getCompetitionSession().getUuid());
    }

    private class AdminPageStatus {
        private List<String> roles;
        private String selectedYearLabel;
        private boolean isWithAdminRole;
        private boolean isWithSecretCurrentYear;
        private AdminPageStatus(Authentication user) {
            roles = user.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority).collect(Collectors.toList());
            selectedYearLabel = competitionService.getSelectedYearLabel();
            isWithAdminRole = roles.contains(Role.ADMIN);
            isWithSecretCurrentYear = selectedYearLabel.contains("2020");

        }
        private void insertPageDefaults(Model model) {
            model.addAttribute("isWithAdminRole", this.isWithAdminRole);
            model.addAttribute("timeLeft", 0);
            model.addAttribute("time", 0);
            model.addAttribute("running", false);
            model.addAttribute("clockStyle", "active");
            model.addAttribute("currentAssignment", "-");
            model.addAttribute("assignmentDetailCanvas", "(U moet eerst de opdrachten inladen)");
            model.addAttribute("gameDetailCanvas", "(U moet eerst de opdrachten inladen)");
            model.addAttribute("opdrachtConfiguraties","(U moet eerst de opdrachten inladen)");
            model.addAttribute("isWithConfigurableTestScore",false);
            model.addAttribute("isWithHiddenTests",false);
            model.addAttribute("activeTeamDetailCanvas", "(U moet eerst de gebruikers aanmaken)");
            model.addAttribute("teamDetailCanvas", "(U moet eerst de gebruikers aanmaken)");
            model.addAttribute("repositoryLocation", mojServerProperties.getAssignmentRepo().toFile());
            model.addAttribute("selectedYearLabel", "");
        }
        private void insertGamestatus(Model model) {
            ActiveAssignment state = competition.getActiveAssignment();
            model.addAttribute("timeLeft", state.getTimeRemaining());
            model.addAttribute("time", state.getAssignmentDescriptor().getDuration().toSeconds());
            model.addAttribute("running", state.isRunning());
            model.addAttribute("clockStyle", (assignmentRuntime.isPaused()?"disabled":"active"));
            model.addAttribute("currentAssignment", state.getAssignmentDescriptor().getName());
        }
        private void insertAssignmentInfo(Model model) {
            List<AssignmentDescriptor> assignmentDescriptorList = competition.getAssignmentInfoOrderedForCompetition();

            boolean isWithAssignmentsLoaded = !assignmentDescriptorList.isEmpty();

            if (isWithAssignmentsLoaded) {
                String assignmentDetailCanvas = gamemasterTableComponents.toSimpleBootstrapTable(assignmentDescriptorList);

                model.addAttribute("isWithConfigurableTestScore",assignmentDetailCanvas.contains("(*2)"));
                model.addAttribute("isWithHiddenTests",assignmentDetailCanvas.contains("(*1)"));
                model.addAttribute("assignmentDetailCanvas",  assignmentDetailCanvas);
                model.addAttribute("gameDetailCanvas", gamemasterTableComponents.toSimpleBootstrapTableForAssignmentStatus());
                model.addAttribute("opdrachtConfiguraties", gamemasterTableComponents.toSimpleBootstrapTablesForFileDetails(assignmentDescriptorList));
            }
            model.addAttribute("assignments",assignmentDescriptorList);
            model.addAttribute("isWithAssignmentsLoaded", isWithAssignmentsLoaded);
        }

        private void validateRoleAuthorization() {
            Assert.isTrue(roles.contains(Role.ADMIN)||roles.contains(Role.GAME_MASTER),"not authorized");
            Assert.isTrue(!isWithSecretCurrentYear||isWithAdminRole,"Gamemasters are not authorized to see secret current year assignments");
        }
        private boolean isDuringCompetitionAssignment() {
            return competition.getCurrentAssignment() != null;
        }
        private void insertCompetitionInfo(Model model) {

            List<Team> teams = team();
            if (!teams.isEmpty() && this.isWithAdminRole) {
                List<Ranking> rankings = rankingsService.getRankings(competition.getCompetitionSession());
                model.addAttribute("teamDetailCanvas", gamemasterTableComponents.toSimpleBootstrapTableForTeams(teams, true, rankings));
                model.addAttribute("activeTeamDetailCanvas",  gamemasterTableComponents.toSimpleBootstrapTableForTeams(teams, false, rankings));
            }
            if (this.isWithAdminRole) {
                model.addAttribute("repositoryLocation", competitionService.getSelectedLocation());
                model.addAttribute("selectedYearLabel", this.selectedYearLabel);
            }

            model.addAttribute("setting_registration_disabled", competitionService.isRegistrationFormDisabled());
            model.addAttribute("sessionDetailCanvas", gamemasterTableComponents.toSimpleBootstrapTableForSessions());

        }
    }

    @GetMapping("/control")
    public String taskControl(Model model, @AuthenticationPrincipal Authentication user, @ModelAttribute("selectSessionForm") SelectSessionForm ssf,
                              @ModelAttribute("newPasswordRequest") NewPasswordRequest npr) {
        if (sessions().isEmpty()) {
            competition.startSession(competition.getCompetition());
        }
        AdminPageStatus pageStatus = new AdminPageStatus(user);
        pageStatus.validateRoleAuthorization();
        pageStatus.insertPageDefaults(model);
        if (pageStatus.isDuringCompetitionAssignment()) {
            pageStatus.insertGamestatus(model);
        }
        pageStatus.insertAssignmentInfo(model);
        pageStatus.insertCompetitionInfo(model);
        ssf.setSession(competition.getCompetitionSession().getUuid());
        return "control";
    }


    @PostMapping("/control/select-session")
    public String selectSession(@ModelAttribute("sessionSelectForm") SelectSessionForm ssf) {
        competition.loadSession(competition.getCompetition(), ssf.getSession());
        return "redirect:/control";
    }

    /**
     * creates new session in selected competition. There is always only on session active in a competition.
     */
    @PostMapping("/control/new-session")
    public String newSession() {
        competition.startSession(competition.getCompetition());
        return "redirect:/control";
    }

    @PostMapping("/control/resetPassword")
    public String resetPassword(RedirectAttributes redirectAttributes,
                                @ModelAttribute("newPasswordRequest") NewPasswordRequest passwordChangeRequest) {

        String errorMessage = null;

        if (passwordChangeRequest.teamUuid.equals("0")) {
            errorMessage = "No team selected";
        } else {
            Team team = teamRepository.findByUuid(UUID.fromString(passwordChangeRequest.teamUuid));
            if (passwordChangeRequest.newPassword == null || passwordChangeRequest.newPassword.isBlank()) {
                errorMessage = "New password can't be empty";
            } else if (!passwordChangeRequest.newPassword.equals(passwordChangeRequest.newPasswordCheck)) {
                errorMessage = "Password and confirmaton did not match";
            } else {
                team.setPassword(competitionService.getEncoder().encode(passwordChangeRequest.newPassword));
                teamRepository.save(team);
                redirectAttributes.addFlashAttribute("success", "Successfully changed password");
                return "redirect:/control";
            }
        }

        passwordChangeRequest.clearPasswords();
        redirectAttributes.addFlashAttribute("newPasswordRequest", passwordChangeRequest);
        redirectAttributes.addFlashAttribute("error", errorMessage);

        return "redirect:/control";
    }

    /**
     * General Taskmessage for GUI-actions
     */
    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TaskMessage {
        private String taskName;
        private String uuid;
        private String value;

        public TaskMessage(String taskName) {
            this.taskName = taskName;
        }
    }

    @Data
    public static class NewPasswordRequest {
        private String teamUuid;
        private String newPassword;
        private String newPasswordCheck;

        public void clearPasswords() {
            newPassword = null;
            newPasswordCheck = null;
        }
    }

    @Data
    public static class SelectSessionForm {
        private UUID session;
    }
}
