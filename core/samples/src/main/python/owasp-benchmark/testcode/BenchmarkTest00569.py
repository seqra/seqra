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

	@app.route('/benchmark/weakrand-01/BenchmarkTest00569', methods=['GET'])
	def BenchmarkTest00569_get():
		return BenchmarkTest00569_post()

	@app.route('/benchmark/weakrand-01/BenchmarkTest00569', methods=['POST'])
	def BenchmarkTest00569_post():
		RESPONSE = ""

		param = ""
		headers = request.headers.getlist("BenchmarkTest00569")
		
		if headers:
			param = headers[0]

		import configparser
		
		bar = 'safe!'
		conf28566 = configparser.ConfigParser()
		conf28566.add_section('section28566')
		conf28566.set('section28566', 'keyA-28566', 'a_Value')
		conf28566.set('section28566', 'keyB-28566', param)
		bar = conf28566.get('section28566', 'keyA-28566')

		import base64
		import secrets
		from helpers.utils import mysession

		num = 'BenchmarkTest00569'[13:]
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

