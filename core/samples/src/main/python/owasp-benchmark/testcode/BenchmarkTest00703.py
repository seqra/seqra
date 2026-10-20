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

	@app.route('/benchmark/weakrand-02/BenchmarkTest00703', methods=['GET'])
	def BenchmarkTest00703_get():
		return BenchmarkTest00703_post()

	@app.route('/benchmark/weakrand-02/BenchmarkTest00703', methods=['POST'])
	def BenchmarkTest00703_post():
		RESPONSE = ""

		param = request.args.get("BenchmarkTest00703")
		if not param:
			param = ""

		superstring = f'65405{param}abcd'
		bar = superstring[len('65405'):len(superstring)-5]

		import random
		from helpers.utils import mysession

		num = 'BenchmarkTest00703'[13:]
		user = f'SafeNancy{num}'
		cookie = f'rememberMe{num}'
		value = str(random.SystemRandom().normalvariate())[2:]

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

