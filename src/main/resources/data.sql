INSERT INTO TEAM (name, password, role) 
VALUES ('control', '$2a$10$keHr81N8GAFUoG2wTmQnCOWxlE1DYYbPDQSP6Re5zrgviPhoLn1E.', 'ROLE_CONTROL');
INSERT INTO TEAM (name, password, role) 
VALUES ('team1', '$2a$10$e.W4XVaxh7H/oEr0uj6ef.4hTiQp0bCbcgHOeo6r7S67.5PN29vJm', 'ROLE_USER');
INSERT INTO TEAM (name, password, role) 
VALUES ('team2', '$2a$10$/f3f9fZOExP7w2ewd0m7I.bvYFnCRMt7lYBEWiXkqVt/0COiWCpvu', 'ROLE_USER');

INSERT INTO RESULT (team, assignment, score)
VALUES ('team1', 'WorkloadBalancer', 0);
INSERT INTO RESULT (team, assignment, score)
VALUES ('team1', 'VirtualCPU', 0);

INSERT INTO RESULT (team, assignment, score)
VALUES ('team2', 'WorkloadBalancer', 0);
INSERT INTO RESULT (team, assignment, score)
VALUES ('team2', 'VirtualCPU', 0);