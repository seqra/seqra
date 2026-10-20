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

	@app.route('/benchmark/weakrand-01/BenchmarkTest00309', methods=['GET'])
	def BenchmarkTest00309_get():
		return BenchmarkTest00309_post()

	@app.route('/benchmark/weakrand-01/BenchmarkTest00309', methods=['POST'])
	def BenchmarkTest00309_post():
		RESPONSE = ""

		import helpers.separate_request
		
		wrapped = helpers.separate_request.request_wrapper(request)
		param = wrapped.get_form_parameter("BenchmarkTest00309")
		if not param:
			param = ""

		import configparser
		
		bar = 'safe!'
		conf45195 = configparser.ConfigParser()
		conf45195.add_section('section45195')
		conf45195.set('section45195', 'keyA-45195', 'a_Value')
		conf45195.set('section45195', 'keyB-45195', param)
		bar = conf45195.get('section45195', 'keyA-45195')

		import random
		from helpers.utils import mysession

		num = 'BenchmarkTest00309'[13:]
		user = f'Randall{num}'
		cookie = f'rememberMe{num}'
		value = str(random.random())[2:]

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

