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

	@app.route('/benchmark/securecookie-00/BenchmarkTest00719', methods=['GET'])
	def BenchmarkTest00719_get():
		return BenchmarkTest00719_post()

	@app.route('/benchmark/securecookie-00/BenchmarkTest00719', methods=['POST'])
	def BenchmarkTest00719_post():
		RESPONSE = ""

		param = request.args.get("BenchmarkTest00719")
		if not param:
			param = ""

		import configparser
		
		bar = 'safe!'
		conf49960 = configparser.ConfigParser()
		conf49960.add_section('section49960')
		conf49960.set('section49960', 'keyA-49960', 'a_Value')
		conf49960.set('section49960', 'keyB-49960', param)
		bar = conf49960.get('section49960', 'keyA-49960')

		from flask import make_response
		import io
		import helpers.utils

		input = ''
		if isinstance(bar, str):
			input = bar.encode('utf-8')
		elif isinstance(bar, io.IOBase):
			input = bar.read(1000)

		cookie = 'SomeCookie'
		value = input.decode('utf-8')

		RESPONSE += (
			f'Created cookie: \'{cookie}\' with value \'{helpers.utils.escape_for_html(value)}\' and secure flag set to false.'
		)

		RESPONSE = make_response(RESPONSE)
		RESPONSE.set_cookie(cookie, value,
			path=request.path,
			secure=False,
			httponly=True)

		return RESPONSE

