"""
蚯蚓.手书修仙传 - 核心算法包安装配置
"""

from setuptools import setup, find_packages

with open("requirements.txt", "r", encoding="utf-8") as f:
    requirements = [line.strip() for line in f if line.strip() and not line.startswith("#")]

setup(
    name="ehs-algorithm",
    version="0.1.0",
    description="蚯蚓.手书修仙传 - 核心算法模块 (Eruca HandScript: Cultivation Saga)",
    author="算法工程师",
    python_requires=">=3.9",
    packages=find_packages(),
    install_requires=requirements,
    extras_require={
        "tflite": ["tensorflow>=2.13.0", "onnx>=1.14.0", "onnx-tf>=1.10.0"],
        "dev": ["pytest>=7.4.0", "pytest-cov>=4.1.0", "jupyter>=1.0.0", "matplotlib>=3.7.0"],
    },
    classifiers=[
        "Development Status :: 3 - Alpha",
        "Intended Audience :: Developers",
        "Programming Language :: Python :: 3.9",
        "Programming Language :: Python :: 3.10",
        "Programming Language :: Python :: 3.11",
    ],
)
