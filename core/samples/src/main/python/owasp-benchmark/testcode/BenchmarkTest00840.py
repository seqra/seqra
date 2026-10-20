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

	@app.route('/benchmark/xss-00/BenchmarkTest00840', methods=['GET'])
	def BenchmarkTest00840_get():
		return BenchmarkTest00840_post()

	@app.route('/benchmark/xss-00/BenchmarkTest00840', methods=['POST'])
	def BenchmarkTest00840_post():
		RESPONSE = ""

		import helpers.separate_request
		
		wrapped = helpers.separate_request.request_wrapper(request)
		param = wrapped.get_query_parameter("BenchmarkTest00840")
		if not param:
			param = ""

		map52374 = {}
		map52374['keyA-52374'] = 'a-Value'
		map52374['keyB-52374'] = param
		map52374['keyC'] = 'another-Value'
		bar = "safe!"
		bar = map52374['keyB-52374']
		bar = map52374['keyA-52374']


		otherarg = "static text"
		RESPONSE += (
			'bar is \'{0}\' and otherarg is \'{1}\''.format(bar, otherarg)
		)

		return RESPONSE

