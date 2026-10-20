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

	@app.route('/benchmark/securecookie-00/BenchmarkTest01207', methods=['GET'])
	def BenchmarkTest01207_get():
		return BenchmarkTest01207_post()

	@app.route('/benchmark/securecookie-00/BenchmarkTest01207', methods=['POST'])
	def BenchmarkTest01207_post():
		RESPONSE = ""

		param = request.args.get("BenchmarkTest01207")
		if not param:
			param = ""


		from flask import make_response
		import io
		import helpers.utils

		input = ''
		if isinstance(param, str):
			input = param.encode('utf-8')
		elif isinstance(param, io.IOBase):
			input = param.read(1000)

		cookie = 'SomeCookie'
		value = input.decode('utf-8')

		RESPONSE += (
			f'Created cookie: \'{cookie}\' with value \'{helpers.utils.escape_for_html(value)}\' and secure flag set to false.'
		)

		RESPONSE = make_response(RESPONSE)
		RESPONSE.set_cookie(cookie, value,
			path=request.path,
			secure=True,
			httponly=True)

		return RESPONSE


