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

	@app.route('/benchmark/weakrand-00/BenchmarkTest00029', methods=['GET'])
	def BenchmarkTest00029_get():
		response = make_response(render_template('web/weakrand-00/BenchmarkTest00029.html'))
		response.set_cookie('BenchmarkTest00029', 'whatever',
			max_age=60*3,
			secure=True,
			path=request.path,
			domain='localhost')
		return response
		return BenchmarkTest00029_post()

	@app.route('/benchmark/weakrand-00/BenchmarkTest00029', methods=['POST'])
	def BenchmarkTest00029_post():
		RESPONSE = ""

		import urllib.parse
		param = urllib.parse.unquote_plus(request.cookies.get("BenchmarkTest00029", "noCookieValueSupplied"))

		import configparser
		
		bar = 'safe!'
		conf93342 = configparser.ConfigParser()
		conf93342.add_section('section93342')
		conf93342.set('section93342', 'keyA-93342', 'a-Value')
		conf93342.set('section93342', 'keyB-93342', param)
		bar = conf93342.get('section93342', 'keyB-93342')

		import random
		from helpers.utils import mysession

		num = 'BenchmarkTest00029'[13:]
		user = f'Isaac{num}'
		cookie = f'rememberMe{num}'
		value = str(random.randint(0, 2**32))

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

