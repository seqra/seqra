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

	@app.route('/benchmark/pathtraver-00/BenchmarkTest00170', methods=['GET'])
	def BenchmarkTest00170_get():
		return BenchmarkTest00170_post()

	@app.route('/benchmark/pathtraver-00/BenchmarkTest00170', methods=['POST'])
	def BenchmarkTest00170_post():
		RESPONSE = ""

		values = request.form.getlist("BenchmarkTest00170")
		param = ""
		if values:
			param = values[0]

		import configparser
		
		bar = 'safe!'
		conf64047 = configparser.ConfigParser()
		conf64047.add_section('section64047')
		conf64047.set('section64047', 'keyA-64047', 'a_Value')
		conf64047.set('section64047', 'keyB-64047', param)
		bar = conf64047.get('section64047', 'keyA-64047')

		import codecs
		import helpers.utils

		try:
			fileTarget = codecs.open(f'{helpers.utils.TESTFILES_DIR}/{bar}','r','utf-8')

			RESPONSE += (
				f"Access to file: \'{escape_for_html(fileTarget.name)}\' created."
			)

			RESPONSE += (
				" And file already exists."
			)

		except FileNotFoundError:
			RESPONSE += (
				" But file doesn't exist yet."
			)

		return RESPONSE

