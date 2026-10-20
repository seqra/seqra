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

	@app.route('/benchmark/weakrand-02/BenchmarkTest00627', methods=['GET'])
	def BenchmarkTest00627_get():
		return BenchmarkTest00627_post()

	@app.route('/benchmark/weakrand-02/BenchmarkTest00627', methods=['POST'])
	def BenchmarkTest00627_post():
		RESPONSE = ""

		import helpers.utils
		param = ""
		
		for name in request.headers.keys():
			if name.lower() in helpers.utils.commonHeaderNames:
				continue
		
			if request.headers.get_all(name):
				param = name
				break

		import configparser
		
		bar = 'safe!'
		conf80161 = configparser.ConfigParser()
		conf80161.add_section('section80161')
		conf80161.set('section80161', 'keyA-80161', 'a_Value')
		conf80161.set('section80161', 'keyB-80161', param)
		bar = conf80161.get('section80161', 'keyA-80161')

		import random
		from helpers.utils import mysession

		num = 'BenchmarkTest00627'[13:]
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

