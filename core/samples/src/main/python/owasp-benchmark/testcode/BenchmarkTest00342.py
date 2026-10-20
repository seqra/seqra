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

	@app.route('/benchmark/codeinj-00/BenchmarkTest00342', methods=['GET'])
	def BenchmarkTest00342_get():
		return BenchmarkTest00342_post()

	@app.route('/benchmark/codeinj-00/BenchmarkTest00342', methods=['POST'])
	def BenchmarkTest00342_post():
		RESPONSE = ""

		import helpers.separate_request
		
		wrapped = helpers.separate_request.request_wrapper(request)
		param = wrapped.get_form_parameter("BenchmarkTest00342")
		if not param:
			param = ""

		import configparser
		
		bar = 'safe!'
		conf31504 = configparser.ConfigParser()
		conf31504.add_section('section31504')
		conf31504.set('section31504', 'keyA-31504', 'a-Value')
		conf31504.set('section31504', 'keyB-31504', param)
		bar = conf31504.get('section31504', 'keyB-31504')

		try:
			RESPONSE += (
				eval(bar)
			)
		except:
			RESPONSE += (
				f'Error evaluating expression \'{escape_for_html(bar)}\''
			)

		return RESPONSE

