'''
OWASP Benchmark for Python v0.1

This file is part of the Open Web Application Security Project (OWASP) Benchmark Project.
For details, please see https://owasp.org/www-project-benchmark.

The OWASP Benchmark is free software: you can redistribute it and/or modify it under the terms
of the GNU General Public License as published by the Free Software Foundation, version 3.

The OWASP Benchmark is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
PURPOSE. See the GNU General Public License for more details.

  Author: Theo Cartsonis
  Created: 2025
'''

from flask import redirect, url_for, request, make_response, render_template
from helpers.utils import escape_for_html

def init(app):

	@app.route('/benchmark/sqli-00/BenchmarkTest00285', methods=['GET'])
	def BenchmarkTest00285_get():
		return BenchmarkTest00285_post()

	@app.route('/benchmark/sqli-00/BenchmarkTest00285', methods=['POST'])
	def BenchmarkTest00285_post():
		RESPONSE = ""

		import helpers.separate_request
		
		wrapped = helpers.separate_request.request_wrapper(request)
		param = wrapped.get_form_parameter("BenchmarkTest00285")
		if not param:
			param = ""

		import configparser
		
		bar = 'safe!'
		conf39490 = configparser.ConfigParser()
		conf39490.add_section('section39490')
		conf39490.set('section39490', 'keyA-39490', 'a-Value')
		conf39490.set('section39490', 'keyB-39490', param)
		bar = conf39490.get('section39490', 'keyB-39490')

		import helpers.db_sqlite

		sql = f'SELECT username from USERS where password = ?'
		con = helpers.db_sqlite.get_connection()
		cur = con.cursor()
		cur.execute(sql, (bar,))
		RESPONSE += (
			helpers.db_sqlite.results(cur, sql)
		)
		con.close()

		return RESPONSE

