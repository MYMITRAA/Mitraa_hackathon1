CREATE TABLE scoring_criteria (
    id BIGINT NOT NULL AUTO_INCREMENT, code VARCHAR(50) NOT NULL, name VARCHAR(120) NOT NULL,
    description VARCHAR(500) NULL, weight_percent DECIMAL(5,2) NOT NULL, display_order INT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id), CONSTRAINT uk_scoring_criterion_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO scoring_criteria(code,name,description,weight_percent,display_order,active,created_at,updated_at) VALUES
('PROBLEM_RELEVANCE','Problem relevance','Importance and clarity of the problem addressed.',15.00,1,TRUE,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6)),
('INNOVATION','Innovation','Originality and differentiation of the proposed solution.',20.00,2,TRUE,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6)),
('TECHNICAL_IMPLEMENTATION','Technical implementation','Architecture, engineering quality and appropriate use of technology.',20.00,3,TRUE,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6)),
('WORKING_POC','Working POC','Completeness, reliability and demonstrability of the proof of concept.',20.00,4,TRUE,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6)),
('IMPACT_SCALABILITY','Impact and scalability','Measurable impact, feasibility and ability to scale.',15.00,5,TRUE,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6)),
('RESPONSIBLE_AI_SECURITY','Responsible AI and security','Privacy, safety, fairness, security and responsible-AI controls.',10.00,6,TRUE,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6));

CREATE TABLE jury_arena_assignments (
    id BIGINT NOT NULL AUTO_INCREMENT, jury_user_id BIGINT NOT NULL, arena VARCHAR(160) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE, assigned_by_user_id BIGINT NOT NULL, assigned_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id), CONSTRAINT uk_jury_arena UNIQUE (jury_user_id,arena),
    CONSTRAINT fk_jury_assignment_user FOREIGN KEY (jury_user_id) REFERENCES users(id),
    CONSTRAINT fk_jury_assignment_admin FOREIGN KEY (assigned_by_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE jury_evaluations (
    id BIGINT NOT NULL AUTO_INCREMENT, jury_user_id BIGINT NOT NULL, submission_id BIGINT NOT NULL,
    conflict_declared BOOLEAN NOT NULL DEFAULT FALSE, has_conflict BOOLEAN NOT NULL DEFAULT FALSE,
    conflict_details VARCHAR(1000) NULL, comments VARCHAR(4000) NULL, status VARCHAR(30) NOT NULL,
    total_score DECIMAL(7,4) NULL, created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
    locked_at TIMESTAMP(6) NULL, PRIMARY KEY (id),
    CONSTRAINT uk_jury_submission_evaluation UNIQUE (jury_user_id,submission_id),
    CONSTRAINT fk_evaluation_jury FOREIGN KEY (jury_user_id) REFERENCES users(id),
    CONSTRAINT fk_evaluation_submission FOREIGN KEY (submission_id) REFERENCES submissions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE jury_evaluation_scores (
    id BIGINT NOT NULL AUTO_INCREMENT, evaluation_id BIGINT NOT NULL, criterion_id BIGINT NOT NULL,
    score DECIMAL(5,2) NOT NULL, weighted_score DECIMAL(7,4) NOT NULL,
    PRIMARY KEY (id), CONSTRAINT uk_evaluation_criterion UNIQUE (evaluation_id,criterion_id),
    CONSTRAINT fk_score_evaluation FOREIGN KEY (evaluation_id) REFERENCES jury_evaluations(id) ON DELETE CASCADE,
    CONSTRAINT fk_score_criterion FOREIGN KEY (criterion_id) REFERENCES scoring_criteria(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE submission_rankings (
    id BIGINT NOT NULL AUTO_INCREMENT, submission_id BIGINT NOT NULL, arena VARCHAR(160) NOT NULL,
    final_score DECIMAL(7,4) NOT NULL, locked_evaluation_count INT NOT NULL, overall_rank INT NULL,
    arena_rank INT NULL, shortlisted BOOLEAN NOT NULL DEFAULT FALSE, generated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id), CONSTRAINT uk_submission_ranking UNIQUE (submission_id),
    CONSTRAINT fk_ranking_submission FOREIGN KEY (submission_id) REFERENCES submissions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE evaluation_overrides (
    id BIGINT NOT NULL AUTO_INCREMENT, evaluation_id BIGINT NOT NULL, criterion_id BIGINT NULL,
    previous_score DECIMAL(7,4) NULL, replacement_score DECIMAL(7,4) NULL,
    justification VARCHAR(1000) NOT NULL, overridden_by_user_id BIGINT NOT NULL, created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id), CONSTRAINT fk_override_evaluation FOREIGN KEY (evaluation_id) REFERENCES jury_evaluations(id),
    CONSTRAINT fk_override_criterion FOREIGN KEY (criterion_id) REFERENCES scoring_criteria(id),
    CONSTRAINT fk_override_admin FOREIGN KEY (overridden_by_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE winner_selections (
    id BIGINT NOT NULL AUTO_INCREMENT, submission_id BIGINT NOT NULL, place_number INT NOT NULL,
    status VARCHAR(20) NOT NULL, selection_justification VARCHAR(1000) NOT NULL,
    selected_by_user_id BIGINT NOT NULL, selected_at TIMESTAMP(6) NOT NULL,
    approved_by_user_id BIGINT NULL, approved_at TIMESTAMP(6) NULL, approval_justification VARCHAR(1000) NULL,
    PRIMARY KEY (id), CONSTRAINT uk_winner_place UNIQUE (place_number), CONSTRAINT uk_winner_submission UNIQUE (submission_id),
    CONSTRAINT fk_winner_submission FOREIGN KEY (submission_id) REFERENCES submissions(id),
    CONSTRAINT fk_winner_selector FOREIGN KEY (selected_by_user_id) REFERENCES users(id),
    CONSTRAINT fk_winner_approver FOREIGN KEY (approved_by_user_id) REFERENCES users(id),
    CONSTRAINT chk_winner_place CHECK (place_number BETWEEN 1 AND 3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_jury_assignment_arena ON jury_arena_assignments(arena,active);
CREATE INDEX idx_evaluation_status ON jury_evaluations(status,locked_at);
CREATE INDEX idx_ranking_order ON submission_rankings(shortlisted,overall_rank);
