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

	@app.route('/benchmark/redirect-00/BenchmarkTest00596', methods=['GET'])
	def BenchmarkTest00596_get():
		return BenchmarkTest00596_post()

	@app.route('/benchmark/redirect-00/BenchmarkTest00596', methods=['POST'])
	def BenchmarkTest00596_post():
		RESPONSE = ""

		param = ""
		headers = request.headers.getlist("BenchmarkTest00596")
		
		if headers:
			param = headers[0]

		import configparser
		
		bar = 'safe!'
		conf96509 = configparser.ConfigParser()
		conf96509.add_section('section96509')
		conf96509.set('section96509', 'keyA-96509', 'a-Value')
		conf96509.set('section96509', 'keyB-96509', param)
		bar = conf96509.get('section96509', 'keyB-96509')

		import flask

		return flask.redirect(bar)

		return RESPONSE

