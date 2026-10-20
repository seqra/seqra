import sqlite3
import helpers.utils

DB_PATH = f'{helpers.utils.TESTFILES_DIR}/benchmark-sqlite3.db'

con = sqlite3.connect(DB_PATH)
con.autocommit = True
cur = con.cursor()
cur.executescript("""
DROP TABLE IF EXISTS USERS;
DROP TABLE IF EXISTS SCORE;
DROP TABLE IF EXISTS EMPLOYEE;
DROP TABLE IF EXISTS CERTIFICATE;

CREATE TABLE USERS (userid INTEGER NOT NULL, username varchar(50), password varchar(50),PRIMARY KEY (userid ASC));
CREATE TABLE SCORE (userid INTEGER NOT NULL, nick varchar(50), score INTEGER,PRIMARY KEY (userid ASC));
CREATE TABLE EMPLOYEE (id INTEGER NOT NULL, first_name VARCHAR(20) default NULL, last_name  VARCHAR(20) default NULL, salary INT default NULL, PRIMARY KEY (id ASC));
CREATE TABLE CERTIFICATE (id INTEGER NOT NULL, certificate_name VARCHAR(30) default NULL, employee_id INT default NULL, PRIMARY KEY (id ASC));

INSERT INTO USERS (username, password) VALUES('User01', 'P455w0rd');
INSERT INTO USERS (username, password) VALUES('User02', 'B3nchM3rk');
INSERT INTO USERS (username, password) VALUES('User03', 'a$c11');
INSERT INTO USERS (username, password) VALUES('foo', 'bar');
INSERT INTO SCORE (nick, score) VALUES('User03', 155);
INSERT INTO SCORE (nick, score) VALUES('foo', 40);
INSERT INTO EMPLOYEE (first_name, last_name, salary) VALUES('foo', 'bar', 34567);
""")
con.close()

def get_connection():
	return sqlite3.connect(DB_PATH)

def results(cur, sql: str):
	ret = '''<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\" \"http://www.w3.org/TR/html4/loose.dtd\">
<html>
<head>
<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">
</head>
<body>
<p>
'''
	if not cur:
		ret += f'Results set is empty for query: {sql}'
		return ret
	ret += 'Your results are:<br>\n'
	for row in cur:
		ret += ', '.join(str(x) for x in row)
		ret += '<br>\n'
	return ret
