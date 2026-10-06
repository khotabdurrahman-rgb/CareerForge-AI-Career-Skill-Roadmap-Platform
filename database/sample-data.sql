-- Optional catalog matching DemoData.java, BEFORE application startup.
-- Not a migration or a repeatable seed: explicit IDs intentionally fail on conflicts.
-- Application initialization owns demonstration users and password hashing.
USE careerforge;
START TRANSACTION;

INSERT INTO skills (id, name) VALUES
    (1, 'Java'), (2, 'HTML'), (3, 'CSS'), (4, 'JavaScript'),
    (5, 'Spring Boot'), (6, 'MySQL'), (7, 'REST API'), (8, 'Git'),
    (9, 'Python'), (10, 'Statistics'), (11, 'Pandas'), (12, 'NumPy'),
    (13, 'Machine Learning'), (14, 'SQL'), (15, 'Data Visualization'),
    (16, 'React'), (17, 'C'), (18, 'Data Structures');

INSERT INTO careers (id, name, description) VALUES
    (1, 'Full Stack Java Developer', 'Build complete web applications, from an intuitive interface to powerful Java services.'),
    (2, 'Data Scientist', 'Turn complex data into meaningful insights with statistical thinking and machine learning.'),
    (3, 'Frontend Developer', 'Craft fast, accessible web experiences that people love to use.'),
    (4, 'Backend Developer', 'Design reliable APIs and services that power modern applications.');

INSERT INTO career_skills (career_id, skill_id) VALUES
    (1, 1), (1, 2), (1, 3), (1, 4), (1, 5), (1, 6), (1, 7), (1, 8),
    (2, 9), (2, 10), (2, 11), (2, 12), (2, 13), (2, 14), (2, 15),
    (3, 2), (3, 3), (3, 4), (3, 8), (3, 16),
    (4, 1), (4, 5), (4, 6), (4, 7), (4, 8);

INSERT INTO resources (id, skill_id, title, url, type) VALUES
    (1, 1, 'Java learning hub', 'https://dev.java/learn/', 'Documentation'),
    (2, 2, 'HTML essentials', 'https://developer.mozilla.org/en-US/docs/Learn_web_development/Core/Structuring_content', 'Guide'),
    (3, 3, 'CSS fundamentals', 'https://developer.mozilla.org/en-US/docs/Learn_web_development/Core/Styling_basics', 'Guide'),
    (4, 4, 'JavaScript guide', 'https://developer.mozilla.org/en-US/docs/Web/JavaScript/Guide', 'Guide'),
    (5, 5, 'Building a RESTful web service', 'https://spring.io/guides/gs/rest-service/', 'Tutorial'),
    (6, 6, 'MySQL reference manual', 'https://dev.mysql.com/doc/refman/8.4/en/', 'Documentation'),
    (7, 7, 'HTTP overview', 'https://developer.mozilla.org/en-US/docs/Web/HTTP/Overview', 'Guide'),
    (8, 8, 'Pro Git book', 'https://git-scm.com/book/en/v2', 'Book'),
    (9, 9, 'Python tutorial', 'https://docs.python.org/3/tutorial/', 'Tutorial'),
    (10, 16, 'Learn React', 'https://react.dev/learn', 'Guide'),
    (11, 10, 'Statistics handbook', 'https://www.itl.nist.gov/div898/handbook/', 'Book'),
    (12, 11, 'Getting started with pandas', 'https://pandas.pydata.org/docs/getting_started/', 'Guide'),
    (13, 12, 'NumPy fundamentals', 'https://numpy.org/doc/stable/user/absolute_beginners.html', 'Guide'),
    (14, 13, 'Scikit-learn user guide', 'https://scikit-learn.org/stable/user_guide.html', 'Guide'),
    (15, 14, 'SQL tutorial', 'https://www.postgresql.org/docs/current/tutorial-sql.html', 'Tutorial'),
    (16, 15, 'Matplotlib tutorials', 'https://matplotlib.org/stable/tutorials/', 'Tutorial'),
    (17, 17, 'GNU C language manual', 'https://www.gnu.org/software/c-intro-and-ref/manual/', 'Book'),
    (18, 18, 'OpenDSA', 'https://opendsa.org/', 'Guide');

COMMIT;
