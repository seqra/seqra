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

	@app.route('/benchmark/codeinj-00/BenchmarkTest00602', methods=['GET'])
	def BenchmarkTest00602_get():
		return BenchmarkTest00602_post()

	@app.route('/benchmark/codeinj-00/BenchmarkTest00602', methods=['POST'])
	def BenchmarkTest00602_post():
		RESPONSE = ""

		param = ""
		headers = request.headers.getlist("BenchmarkTest00602")
		
		if headers:
			param = headers[0]

		import configparser
		
		bar = 'safe!'
		conf75466 = configparser.ConfigParser()
		conf75466.add_section('section75466')
		conf75466.set('section75466', 'keyA-75466', 'a_Value')
		conf75466.set('section75466', 'keyB-75466', param)
		bar = conf75466.get('section75466', 'keyA-75466')

		try:
			exec(bar)
		except:
			RESPONSE += (
				f'Error executing statement \'{escape_for_html(bar)}\''
			)

		return RESPONSE

