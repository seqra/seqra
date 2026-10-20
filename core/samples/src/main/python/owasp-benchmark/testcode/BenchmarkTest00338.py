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

	@app.route('/benchmark/trustbound-00/BenchmarkTest00338', methods=['GET'])
	def BenchmarkTest00338_get():
		return BenchmarkTest00338_post()

	@app.route('/benchmark/trustbound-00/BenchmarkTest00338', methods=['POST'])
	def BenchmarkTest00338_post():
		RESPONSE = ""

		import helpers.separate_request
		
		wrapped = helpers.separate_request.request_wrapper(request)
		param = wrapped.get_form_parameter("BenchmarkTest00338")
		if not param:
			param = ""

		import configparser
		
		bar = 'safe!'
		conf51455 = configparser.ConfigParser()
		conf51455.add_section('section51455')
		conf51455.set('section51455', 'keyA-51455', 'a-Value')
		conf51455.set('section51455', 'keyB-51455', param)
		bar = conf51455.get('section51455', 'keyB-51455')

		import flask

		flask.session['userid'] = bar

		RESPONSE += (
			f'Item: \'userid\' with value \'{escape_for_html(bar)}'
			'\'saved in session.'
		)

		return RESPONSE

