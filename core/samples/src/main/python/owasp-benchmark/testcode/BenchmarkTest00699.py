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

	@app.route('/benchmark/weakrand-02/BenchmarkTest00699', methods=['GET'])
	def BenchmarkTest00699_get():
		return BenchmarkTest00699_post()

	@app.route('/benchmark/weakrand-02/BenchmarkTest00699', methods=['POST'])
	def BenchmarkTest00699_post():
		RESPONSE = ""

		param = request.args.get("BenchmarkTest00699")
		if not param:
			param = ""

		map61685 = {}
		map61685['keyA-61685'] = 'a-Value'
		map61685['keyB-61685'] = param
		map61685['keyC'] = 'another-Value'
		bar = "safe!"
		bar = map61685['keyB-61685']
		bar = map61685['keyA-61685']

		import base64
		import secrets
		from helpers.utils import mysession

		num = 'BenchmarkTest00699'[13:]
		user = f'SafeTruman{num}'
		cookie = f'rememberMe{num}'
		value = secrets.token_urlsafe(32)

		if cookie in mysession and request.cookies.get(cookie) == mysession[cookie]:
			RESPONSE += (
				f'Welcome back: {user}<br/>'
			)
		else:
			mysession[cookie] = value
			RESPONSE += (
				f'{user} has been remembered with cookie:'
				f'{cookie} whose value is: {mysession[cookie]}<br/>'
			)

		return RESPONSE

