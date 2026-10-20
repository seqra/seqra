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

	@app.route('/benchmark/weakrand-02/BenchmarkTest00864', methods=['GET'])
	def BenchmarkTest00864_get():
		return BenchmarkTest00864_post()

	@app.route('/benchmark/weakrand-02/BenchmarkTest00864', methods=['POST'])
	def BenchmarkTest00864_post():
		RESPONSE = ""

		import helpers.separate_request
		
		wrapped = helpers.separate_request.request_wrapper(request)
		param = wrapped.get_query_parameter("BenchmarkTest00864")
		if not param:
			param = ""

		import configparser
		
		bar = 'safe!'
		conf5042 = configparser.ConfigParser()
		conf5042.add_section('section5042')
		conf5042.set('section5042', 'keyA-5042', 'a-Value')
		conf5042.set('section5042', 'keyB-5042', param)
		bar = conf5042.get('section5042', 'keyB-5042')

		import secrets
		from helpers.utils import mysession

		num = 'BenchmarkTest00864'[13:]
		user = f'SafeRicky{num}'
		cookie = f'rememberMe{num}'
		value = str(secrets.randbits(32))

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

