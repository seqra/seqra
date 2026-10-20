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

	@app.route('/benchmark/weakrand-00/BenchmarkTest00042', methods=['GET'])
	def BenchmarkTest00042_get():
		response = make_response(render_template('web/weakrand-00/BenchmarkTest00042.html'))
		response.set_cookie('BenchmarkTest00042', 'whatever',
			max_age=60*3,
			secure=True,
			path=request.path,
			domain='localhost')
		return response
		return BenchmarkTest00042_post()

	@app.route('/benchmark/weakrand-00/BenchmarkTest00042', methods=['POST'])
	def BenchmarkTest00042_post():
		RESPONSE = ""

		import urllib.parse
		param = urllib.parse.unquote_plus(request.cookies.get("BenchmarkTest00042", "noCookieValueSupplied"))

		map52993 = {}
		map52993['keyA-52993'] = 'a-Value'
		map52993['keyB-52993'] = param
		map52993['keyC'] = 'another-Value'
		bar = "safe!"
		bar = map52993['keyB-52993']
		bar = map52993['keyA-52993']

		import base64
		import secrets
		from helpers.utils import mysession

		num = 'BenchmarkTest00042'[13:]
		user = f'SafeTheo{num}'
		cookie = f'rememberMe{num}'
		value = secrets.token_hex(32)

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

