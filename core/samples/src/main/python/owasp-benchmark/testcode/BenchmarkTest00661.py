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

	@app.route('/benchmark/pathtraver-01/BenchmarkTest00661', methods=['GET'])
	def BenchmarkTest00661_get():
		return BenchmarkTest00661_post()

	@app.route('/benchmark/pathtraver-01/BenchmarkTest00661', methods=['POST'])
	def BenchmarkTest00661_post():
		RESPONSE = ""

		param = request.args.get("BenchmarkTest00661")
		if not param:
			param = ""

		import configparser
		
		bar = 'safe!'
		conf85641 = configparser.ConfigParser()
		conf85641.add_section('section85641')
		conf85641.set('section85641', 'keyA-85641', 'a_Value')
		conf85641.set('section85641', 'keyB-85641', param)
		bar = conf85641.get('section85641', 'keyA-85641')

		import helpers.utils

		fileName = None
		fd = None

		try:
			fileName = f'{helpers.utils.TESTFILES_DIR}/{bar}'
			fd = open(fileName, 'rb')
			RESPONSE += (
				f'The beginning of file: \'{escape_for_html(fileName)}\' is:\n\n'
				f'{escape_for_html(fd.read(1000).decode('utf-8'))}'
			)
		except IOError as e:
			RESPONSE += (
				f'Problem reading from file \'{{escape_for_html(fileName)}}\': '
				f'{escape_for_html(e.strerror)}'
			)
		finally:
			try:
				if fd is not None:
					fd.close()
			except IOError:
				pass # "// we tried..."

		return RESPONSE

