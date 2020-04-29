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
package nl.moj.server.runtime;

import java.util.List;

import nl.moj.server.rankings.model.Ranking;
import nl.moj.server.rankings.service.RankingsService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest
public class RankingServiceTest extends BaseRuntimeTest {

    @Autowired
    private RankingsService rankingsService;

    @Autowired
    private CompetitionRuntime competitionRuntime;

    @Test
    public void shouldGiveRankingsWithZeroScoreIfNoResultsAreFound() {
        // add some extra teams to trigger sorting, its smart and wont
        // do anything with only one team.
        addTeam();
        addTeam();

        List<Ranking> rankings = rankingsService.getRankings(competitionRuntime.getCompetitionSession());
        assertThat(rankings).isNotEmpty();
        rankings.forEach(r -> assertThat(r.getTotalScore()).isEqualTo(0));
    }
}
