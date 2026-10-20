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

	@app.route('/benchmark/xss-01/BenchmarkTest01103', methods=['GET'])
	def BenchmarkTest01103_get():
		return BenchmarkTest01103_post()

	@app.route('/benchmark/xss-01/BenchmarkTest01103', methods=['POST'])
	def BenchmarkTest01103_post():
		RESPONSE = ""

		import helpers.separate_request
		scr = helpers.separate_request.request_wrapper(request)
		param = scr.get_safe_value("BenchmarkTest01103")

		map63777 = {}
		map63777['keyA-63777'] = 'a-Value'
		map63777['keyB-63777'] = param
		map63777['keyC'] = 'another-Value'
		bar = map63777['keyB-63777']


		RESPONSE += (
			f'Parameter value: {bar}'
		)

		return RESPONSE

