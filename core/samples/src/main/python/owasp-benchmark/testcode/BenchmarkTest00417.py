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

	@app.route('/benchmark/redirect-00/BenchmarkTest00417', methods=['GET'])
	def BenchmarkTest00417_get():
		return BenchmarkTest00417_post()

	@app.route('/benchmark/redirect-00/BenchmarkTest00417', methods=['POST'])
	def BenchmarkTest00417_post():
		RESPONSE = ""

		param = ""
		for name in request.form.keys():
			if "BenchmarkTest00417" in request.form.getlist(name):
				param = name
				break

		import configparser
		
		bar = 'safe!'
		conf87573 = configparser.ConfigParser()
		conf87573.add_section('section87573')
		conf87573.set('section87573', 'keyA-87573', 'a_Value')
		conf87573.set('section87573', 'keyB-87573', param)
		bar = conf87573.get('section87573', 'keyA-87573')

		import flask

		return flask.redirect(bar)

		return RESPONSE

