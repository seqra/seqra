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

	@app.route('/benchmark/xss-00/BenchmarkTest00670', methods=['GET'])
	def BenchmarkTest00670_get():
		return BenchmarkTest00670_post()

	@app.route('/benchmark/xss-00/BenchmarkTest00670', methods=['POST'])
	def BenchmarkTest00670_post():
		RESPONSE = ""

		param = request.args.get("BenchmarkTest00670")
		if not param:
			param = ""

		string49442 = ''
		data12 = ''
		copy = string49442
		string49442 = ''
		string49442 += param
		copy += 'SomeOKString'
		bar = copy


		otherarg = "static text"
		RESPONSE += (
			'bar is \'{0}\' and otherarg is \'{1}\''.format(bar, otherarg)
		)

		return RESPONSE

