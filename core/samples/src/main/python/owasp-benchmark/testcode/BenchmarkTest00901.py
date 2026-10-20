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

	@app.route('/benchmark/deserialization-00/BenchmarkTest00901', methods=['GET'])
	def BenchmarkTest00901_get():
		return BenchmarkTest00901_post()

	@app.route('/benchmark/deserialization-00/BenchmarkTest00901', methods=['POST'])
	def BenchmarkTest00901_post():
		RESPONSE = ""

		import helpers.separate_request
		
		wrapped = helpers.separate_request.request_wrapper(request)
		param = wrapped.get_query_parameter("BenchmarkTest00901")
		if not param:
			param = ""

		import configparser
		
		bar = 'safe!'
		conf39670 = configparser.ConfigParser()
		conf39670.add_section('section39670')
		conf39670.set('section39670', 'keyA-39670', 'a_Value')
		conf39670.set('section39670', 'keyB-39670', param)
		bar = conf39670.get('section39670', 'keyA-39670')

		import yaml

		try:
			yobj = yaml.safe_load(bar)

			RESPONSE += (
				yobj['text']
			)
		except:
			RESPONSE += (
				"There was an error loading the configuration"
			)

		return RESPONSE

