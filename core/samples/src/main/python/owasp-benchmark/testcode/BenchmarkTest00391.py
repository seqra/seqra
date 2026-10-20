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

	@app.route('/benchmark/weakrand-01/BenchmarkTest00391', methods=['GET'])
	def BenchmarkTest00391_get():
		return BenchmarkTest00391_post()

	@app.route('/benchmark/weakrand-01/BenchmarkTest00391', methods=['POST'])
	def BenchmarkTest00391_post():
		RESPONSE = ""

		param = ""
		for name in request.form.keys():
			if "BenchmarkTest00391" in request.form.getlist(name):
				param = name
				break

		import configparser
		
		bar = 'safe!'
		conf2310 = configparser.ConfigParser()
		conf2310.add_section('section2310')
		conf2310.set('section2310', 'keyA-2310', 'a_Value')
		conf2310.set('section2310', 'keyB-2310', param)
		bar = conf2310.get('section2310', 'keyA-2310')

		import base64
		import secrets
		from helpers.utils import mysession

		num = 'BenchmarkTest00391'[13:]
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

