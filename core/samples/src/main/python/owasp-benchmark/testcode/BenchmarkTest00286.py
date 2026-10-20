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

	@app.route('/benchmark/sqli-00/BenchmarkTest00286', methods=['GET'])
	def BenchmarkTest00286_get():
		return BenchmarkTest00286_post()

	@app.route('/benchmark/sqli-00/BenchmarkTest00286', methods=['POST'])
	def BenchmarkTest00286_post():
		RESPONSE = ""

		import helpers.separate_request
		
		wrapped = helpers.separate_request.request_wrapper(request)
		param = wrapped.get_form_parameter("BenchmarkTest00286")
		if not param:
			param = ""

		import configparser
		
		bar = 'safe!'
		conf59255 = configparser.ConfigParser()
		conf59255.add_section('section59255')
		conf59255.set('section59255', 'keyA-59255', 'a_Value')
		conf59255.set('section59255', 'keyB-59255', param)
		bar = conf59255.get('section59255', 'keyA-59255')

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

