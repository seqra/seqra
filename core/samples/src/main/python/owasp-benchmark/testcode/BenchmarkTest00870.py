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

	@app.route('/benchmark/weakrand-02/BenchmarkTest00870', methods=['GET'])
	def BenchmarkTest00870_get():
		return BenchmarkTest00870_post()

	@app.route('/benchmark/weakrand-02/BenchmarkTest00870', methods=['POST'])
	def BenchmarkTest00870_post():
		RESPONSE = ""

		import helpers.separate_request
		
		wrapped = helpers.separate_request.request_wrapper(request)
		param = wrapped.get_query_parameter("BenchmarkTest00870")
		if not param:
			param = ""

		map42836 = {}
		map42836['keyA-42836'] = 'a-Value'
		map42836['keyB-42836'] = param
		map42836['keyC'] = 'another-Value'
		bar = "safe!"
		bar = map42836['keyB-42836']
		bar = map42836['keyA-42836']

		import random
		import base64
		from helpers.utils import mysession

		num = 'BenchmarkTest00870'[13:]
		user = f'SafeBarbara{num}'
		cookie = f'rememberMe{num}'
		value = str(base64.b64encode(random.SystemRandom().randbytes(32)))

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

