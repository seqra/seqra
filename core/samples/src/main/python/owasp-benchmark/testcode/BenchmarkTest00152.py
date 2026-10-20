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

	@app.route('/benchmark/trustbound-00/BenchmarkTest00152', methods=['GET'])
	def BenchmarkTest00152_get():
		return BenchmarkTest00152_post()

	@app.route('/benchmark/trustbound-00/BenchmarkTest00152', methods=['POST'])
	def BenchmarkTest00152_post():
		RESPONSE = ""

		param = request.form.get("BenchmarkTest00152")
		if not param:
			param = ""

		import configparser
		
		bar = 'safe!'
		conf32394 = configparser.ConfigParser()
		conf32394.add_section('section32394')
		conf32394.set('section32394', 'keyA-32394', 'a_Value')
		conf32394.set('section32394', 'keyB-32394', param)
		bar = conf32394.get('section32394', 'keyA-32394')

		import flask

		flask.session['userid'] = bar

		RESPONSE += (
			f'Item: \'userid\' with value \'{escape_for_html(bar)}'
			'\'saved in session.'
		)

		return RESPONSE

