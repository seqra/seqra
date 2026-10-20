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

	@app.route('/benchmark/weakrand-01/BenchmarkTest00408', methods=['GET'])
	def BenchmarkTest00408_get():
		return BenchmarkTest00408_post()

	@app.route('/benchmark/weakrand-01/BenchmarkTest00408', methods=['POST'])
	def BenchmarkTest00408_post():
		RESPONSE = ""

		param = ""
		for name in request.form.keys():
			if "BenchmarkTest00408" in request.form.getlist(name):
				param = name
				break

		num = 106
		
		bar = "This_should_always_happen" if 7 * 18 + num > 200 else param

		import random
		from helpers.utils import mysession

		num = 'BenchmarkTest00408'[13:]
		user = f'SafeRandall{num}'
		cookie = f'rememberMe{num}'
		value = str(random.SystemRandom().random())[2:]

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

