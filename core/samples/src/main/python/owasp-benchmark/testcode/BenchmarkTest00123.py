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

	@app.route('/benchmark/weakrand-00/BenchmarkTest00123', methods=['GET'])
	def BenchmarkTest00123_get():
		return BenchmarkTest00123_post()

	@app.route('/benchmark/weakrand-00/BenchmarkTest00123', methods=['POST'])
	def BenchmarkTest00123_post():
		RESPONSE = ""

		param = request.form.get("BenchmarkTest00123")
		if not param:
			param = ""

		import helpers.ThingFactory
		
		thing = helpers.ThingFactory.createThing()
		bar = thing.doSomething(param)

		import base64
		import secrets
		from helpers.utils import mysession

		num = 'BenchmarkTest00123'[13:]
		user = f'SafeToby{num}'
		cookie = f'rememberMe{num}'
		value = base64.b64encode(secrets.token_bytes(32))

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

