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

	@app.route('/benchmark/deserialization-00/BenchmarkTest00081', methods=['GET'])
	def BenchmarkTest00081_get():
		response = make_response(render_template('web/deserialization-00/BenchmarkTest00081.html'))
		response.set_cookie('BenchmarkTest00081', 'name%3A+safe+data%0Atext%3A+act+like+this+is+conf+data',
			max_age=60*3,
			secure=True,
			path=request.path,
			domain='localhost')
		return response
		return BenchmarkTest00081_post()

	@app.route('/benchmark/deserialization-00/BenchmarkTest00081', methods=['POST'])
	def BenchmarkTest00081_post():
		RESPONSE = ""

		import urllib.parse
		param = urllib.parse.unquote_plus(request.cookies.get("BenchmarkTest00081", "noCookieValueSupplied"))

		string75664 = ''
		data12 = ''
		copy = string75664
		string75664 = ''
		string75664 += param
		copy += 'SomeOKString'
		bar = copy

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

