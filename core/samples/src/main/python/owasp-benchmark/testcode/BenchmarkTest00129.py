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

	@app.route('/benchmark/weakrand-00/BenchmarkTest00129', methods=['GET'])
	def BenchmarkTest00129_get():
		return BenchmarkTest00129_post()

	@app.route('/benchmark/weakrand-00/BenchmarkTest00129', methods=['POST'])
	def BenchmarkTest00129_post():
		RESPONSE = ""

		param = request.form.get("BenchmarkTest00129")
		if not param:
			param = ""

		import configparser
		
		bar = 'safe!'
		conf27994 = configparser.ConfigParser()
		conf27994.add_section('section27994')
		conf27994.set('section27994', 'keyA-27994', 'a-Value')
		conf27994.set('section27994', 'keyB-27994', param)
		bar = conf27994.get('section27994', 'keyB-27994')

		import random
		from helpers.utils import mysession

		num = 'BenchmarkTest00129'[13:]
		user = f'SafeRandy{num}'
		cookie = f'rememberMe{num}'
		value = str(random.SystemRandom().getrandbits(32))

		if cookie in mysession and request.cookies.get(cookie) == mysession[cookie]:
			RESPONSE += (
				f'Welcome back: {user}<br/>'
			)
		else:
			mysession[cookie] = value
			RESPONSE += (
				f'{user} has been remembered with cookie: '
				f'{cookie} whose value is: {mysession[cookie]}<br/>'
			)

		return RESPONSE

