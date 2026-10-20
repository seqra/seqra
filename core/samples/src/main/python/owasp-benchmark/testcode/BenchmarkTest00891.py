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

	@app.route('/benchmark/codeinj-00/BenchmarkTest00891', methods=['GET'])
	def BenchmarkTest00891_get():
		return BenchmarkTest00891_post()

	@app.route('/benchmark/codeinj-00/BenchmarkTest00891', methods=['POST'])
	def BenchmarkTest00891_post():
		RESPONSE = ""

		import helpers.separate_request
		
		wrapped = helpers.separate_request.request_wrapper(request)
		param = wrapped.get_query_parameter("BenchmarkTest00891")
		if not param:
			param = ""

		import configparser
		
		bar = 'safe!'
		conf8968 = configparser.ConfigParser()
		conf8968.add_section('section8968')
		conf8968.set('section8968', 'keyA-8968', 'a-Value')
		conf8968.set('section8968', 'keyB-8968', param)
		bar = conf8968.get('section8968', 'keyB-8968')

		try:
			RESPONSE += (
				eval(bar)
			)
		except:
			RESPONSE += (
				f'Error evaluating expression \'{escape_for_html(bar)}\''
			)

		return RESPONSE

