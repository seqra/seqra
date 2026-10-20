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

	@app.route('/benchmark/weakrand-00/BenchmarkTest00027', methods=['GET'])
	def BenchmarkTest00027_get():
		response = make_response(render_template('web/weakrand-00/BenchmarkTest00027.html'))
		response.set_cookie('BenchmarkTest00027', 'whatever',
			max_age=60*3,
			secure=True,
			path=request.path,
			domain='localhost')
		return response
		return BenchmarkTest00027_post()

	@app.route('/benchmark/weakrand-00/BenchmarkTest00027', methods=['POST'])
	def BenchmarkTest00027_post():
		RESPONSE = ""

		import urllib.parse
		param = urllib.parse.unquote_plus(request.cookies.get("BenchmarkTest00027", "noCookieValueSupplied"))

		map18384 = {}
		map18384['keyA-18384'] = 'a-Value'
		map18384['keyB-18384'] = param
		map18384['keyC'] = 'another-Value'
		bar = map18384['keyB-18384']

		import random
		import base64
		from helpers.utils import mysession

		num = 'BenchmarkTest00027'[13:]
		user = f'Barbara{num}'
		cookie = f'rememberMe{num}'
		value = str(base64.b64encode(random.randbytes(32)))

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

