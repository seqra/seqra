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

	@app.route('/benchmark/weakrand-02/BenchmarkTest00688', methods=['GET'])
	def BenchmarkTest00688_get():
		return BenchmarkTest00688_post()

	@app.route('/benchmark/weakrand-02/BenchmarkTest00688', methods=['POST'])
	def BenchmarkTest00688_post():
		RESPONSE = ""

		param = request.args.get("BenchmarkTest00688")
		if not param:
			param = ""

		map87432 = {}
		map87432['keyA-87432'] = 'a-Value'
		map87432['keyB-87432'] = param
		map87432['keyC'] = 'another-Value'
		bar = map87432['keyB-87432']

		import random
		from helpers.utils import mysession

		num = 'BenchmarkTest00688'[13:]
		user = f'Randy{num}'
		cookie = f'rememberMe{num}'
		value = str(random.getrandbits(32))

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

