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

	@app.route('/benchmark/xss-00/BenchmarkTest01017', methods=['GET'])
	def BenchmarkTest01017_get():
		return BenchmarkTest01017_post()

	@app.route('/benchmark/xss-00/BenchmarkTest01017', methods=['POST'])
	def BenchmarkTest01017_post():
		RESPONSE = ""

		parts = request.path.split("/")
		param = parts[1]
		if not param:
			param = ""

		map25383 = {}
		map25383['keyA-25383'] = 'a-Value'
		map25383['keyB-25383'] = param
		map25383['keyC'] = 'another-Value'
		bar = "safe!"
		bar = map25383['keyB-25383']
		bar = map25383['keyA-25383']


		otherarg = "static text"
		RESPONSE += (
			'bar is \'{0}\' and otherarg is \'{1}\''.format(bar, otherarg)
		)

		return RESPONSE

