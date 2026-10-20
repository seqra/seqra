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

	@app.route('/benchmark/pathtraver-00/BenchmarkTest00175', methods=['GET'])
	def BenchmarkTest00175_get():
		return BenchmarkTest00175_post()

	@app.route('/benchmark/pathtraver-00/BenchmarkTest00175', methods=['POST'])
	def BenchmarkTest00175_post():
		RESPONSE = ""

		values = request.form.getlist("BenchmarkTest00175")
		param = ""
		if values:
			param = values[0]

		import configparser
		
		bar = 'safe!'
		conf15913 = configparser.ConfigParser()
		conf15913.add_section('section15913')
		conf15913.set('section15913', 'keyA-15913', 'a-Value')
		conf15913.set('section15913', 'keyB-15913', param)
		bar = conf15913.get('section15913', 'keyB-15913')

		import helpers.utils

		try:
			fileName = f'{helpers.utils.TESTFILES_DIR}/{bar}'
			fd = open(fileName, 'wb')
			RESPONSE += (
				f'Now ready to write to file: {escape_for_html(fileName)}'
			)
		except IOError as e:
			RESPONSE += (
				f'Problem reading from file \'{escape_for_html(fileName)}\': '
				f'{escape_for_html(e.strerror)}'
			)
		finally:
			try:
				if fd is not None:
					fd.close()
			except IOError:
				pass # "// we tried..."

		return RESPONSE

