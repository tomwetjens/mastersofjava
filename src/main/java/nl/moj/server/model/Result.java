package nl.moj.server.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import nl.moj.server.teams.model.Team;

import javax.persistence.*;

@Entity
@NoArgsConstructor(force = true)
@AllArgsConstructor
@Data
@Table(name = "result")
public class Result {

    public Result(Team team, String assignment, Integer score, Integer penalty, Integer credit) {
        super();
        this.team = team;
        this.assignment = assignment;
        this.score = score;
        this.penalty = penalty;
        this.credit = credit;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
	private Long id;

    @ManyToOne
    @JoinColumn(name="team_id", nullable = false)
	private Team team;

    @Column(name = "assignment", nullable = false)
	private String assignment;

    @Column(name = "score", nullable = false)
	private Integer score;

    @Column(name = "penalty", nullable = false)
	private Integer penalty;

    @Column(name = "credit", nullable = false)
	private Integer credit;
    
}
