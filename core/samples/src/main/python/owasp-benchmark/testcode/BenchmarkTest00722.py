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

	@app.route('/benchmark/redirect-00/BenchmarkTest00722', methods=['GET'])
	def BenchmarkTest00722_get():
		return BenchmarkTest00722_post()

	@app.route('/benchmark/redirect-00/BenchmarkTest00722', methods=['POST'])
	def BenchmarkTest00722_post():
		RESPONSE = ""

		param = request.args.get("BenchmarkTest00722")
		if not param:
			param = ""

		import configparser
		
		bar = 'safe!'
		conf86126 = configparser.ConfigParser()
		conf86126.add_section('section86126')
		conf86126.set('section86126', 'keyA-86126', 'a_Value')
		conf86126.set('section86126', 'keyB-86126', param)
		bar = conf86126.get('section86126', 'keyA-86126')

		import flask

		return flask.redirect(bar)

		return RESPONSE

